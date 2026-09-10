"""
Dead reckoning pipeline for SIH 26168 (GNSS-denied ground vehicle positioning).

Pipeline: raw IMU + intermittent GPS  ->  Stage 4 (Kalman Filter with bias
estimation, ZUPT, GPS-innovation-gated updates, blackout-confidence-decayed
NHC)  ->  Stage 7 (endpoint-anchored map-matching through the GPS blackout,
against live OpenStreetMap road geometry)  ->  corrected position track.

This version supersedes an earlier export of this file that shipped with a
stub `map_match_segment` (it would raise NotImplementedError if called) and
no GPS-fix validation. Both are fixed here:

  - Stage 7 is now the real, validated endpoint-anchored recalibration
    (OSMnx routing between the last-good and first-reacquired GPS fixes,
    position placed along the route using the KF's own speed profile).
  - GPS updates are now innovation-gated: a new fix is only accepted if it's
    statistically consistent with the filter's current belief (chi-squared
    test on the innovation), so a single bad/noisy fix on GPS reacquisition
    can't yank the state to a wrong position.
  - NHC's trust in its own "lateral velocity = 0" assumption now decays the
    longer a GPS blackout runs (R_nhc grows with elapsed blackout time),
    since the filter's believed heading becomes less reliable the longer it
    goes uncorrected -- a fixed, highly-confident NHC was found to produce
    a violent one-step correction partway through some blackouts.

The learned speed model and its learned-covariance companion are NOT wired
in here -- they're separate trained artifacts (a .tflite file and its
covariance-model counterpart), not something that belongs hardcoded into a
plain Python module. See the accompanying model files and the
`speed_pred_lookup` / `r_speed_lookup` parameters on `run_kf` if you want to
fuse them in; this file runs correctly without them (Stage 4 + Stage 7
alone, no learned-speed fusion).
"""

import time
import re
from pathlib import Path

import numpy as np
import pandas as pd


# ---------------------------------------------------------------------------
# Column helpers (raw CSVs have inconsistent whitespace/casing across trips)
# ---------------------------------------------------------------------------

def normalize_columns(df):
    df.columns = [re.sub(r"\s+", " ", c).strip() for c in df.columns]
    return df


def find_col(cols, keyword):
    matches = [c for c in cols if keyword.lower() in c.lower()]
    return matches[0] if matches else None


# ---------------------------------------------------------------------------
# Sensor preprocessing: raw CSV -> world-frame accel + gyro + GPS position
# ---------------------------------------------------------------------------

def load_and_process_trip(csv_path, encoding="cp1252"):
    """
    Reads one trip's raw sensor CSV and returns a DataFrame with:
      t_align, acc_forward, acc_lateral, acc_vertical,
      gyro_yaw, gyro_pitch, gyro_roll, lat, lon, orientation_yaw_raw
    """
    df = pd.read_csv(csv_path, encoding=encoding)
    df = normalize_columns(df)

    time_col = find_col(df.columns, "TIME SINCE START")
    lat_col = find_col(df.columns, "GPS LATITUDE")
    lon_col = find_col(df.columns, "GPS LONGITUDE")
    orient_yaw = find_col(df.columns, "ORIENTATION (Yaw")
    orient_pitch = find_col(df.columns, "ORIENTATION (Pitch")
    orient_roll = find_col(df.columns, "ORIENTATION (Roll")
    acc_cols = [find_col(df.columns, "ACCELEROMETER X"),
                find_col(df.columns, "ACCELEROMETER Y"),
                find_col(df.columns, "ACCELEROMETER Z")]
    grav_cols = [find_col(df.columns, "GRAVITY X"),
                 find_col(df.columns, "GRAVITY Y"),
                 find_col(df.columns, "GRAVITY Z")]
    gyro_cols = [find_col(df.columns, "GYROSCOPE Yaw"),
                 find_col(df.columns, "GYROSCOPE Pitch"),
                 find_col(df.columns, "GYROSCOPE Roll")]

    required = [time_col, lat_col, lon_col, orient_yaw, orient_pitch, orient_roll] \
        + acc_cols + grav_cols + gyro_cols
    if any(c is None for c in required):
        raise ValueError(f"Missing expected column(s) in {csv_path}")

    df["t_align"] = (df[time_col] - df[time_col].iloc[0]) / (1000 if "ms" in time_col.lower() else 1)
    df = df.sort_values("t_align").reset_index(drop=True)

    # corrected heading convention: Android's ORIENTATION(Yaw) is a compass bearing
    # (0=North, clockwise+); math convention needs 0=East, counter-clockwise+.
    yaw_ = np.unwrap(np.radians(90.0 - df[orient_yaw].values))
    pitch_ = np.unwrap(np.radians(df[orient_pitch].values))
    roll_ = np.unwrap(np.radians(df[orient_roll].values))
    cy, sy = np.cos(yaw_), np.sin(yaw_)
    cp, sp = np.cos(pitch_), np.sin(pitch_)
    cr, sr = np.cos(roll_), np.sin(roll_)

    N = len(df)
    R = np.empty((N, 3, 3))
    R[:, 0, 0] = cy * cp
    R[:, 0, 1] = cy * sp * sr - sy * cr
    R[:, 0, 2] = cy * sp * cr + sy * sr
    R[:, 1, 0] = sy * cp
    R[:, 1, 1] = sy * sp * sr + cy * cr
    R[:, 1, 2] = sy * sp * cr - cy * sr
    R[:, 2, 0] = -sp
    R[:, 2, 1] = cp * sr
    R[:, 2, 2] = cp * cr

    lin_acc = df[acc_cols].values - df[grav_cols].values
    a_world = np.einsum("nij,nj->ni", R, lin_acc)

    df["acc_forward"], df["acc_lateral"], df["acc_vertical"] = a_world[:, 0], a_world[:, 1], a_world[:, 2]
    df["gyro_yaw"], df["gyro_pitch"], df["gyro_roll"] = df[gyro_cols[0]], df[gyro_cols[1]], df[gyro_cols[2]]
    df = df.rename(columns={lat_col: "lat", lon_col: "lon"})

    return df[["t_align", "acc_forward", "acc_lateral", "acc_vertical",
               "gyro_yaw", "gyro_pitch", "gyro_roll", "lat", "lon", orient_yaw]].rename(
        columns={orient_yaw: "orientation_yaw_raw"})


# ---------------------------------------------------------------------------
# Stage 4: Kalman Filter (bias estimation + ZUPT + confidence-decayed NHC
#          + innovation-gated GPS updates)
# ---------------------------------------------------------------------------

def init_kf(gps_pos0):
    x = np.zeros(9)
    x[0], x[1] = gps_pos0[0], gps_pos0[1]
    P = np.eye(9)
    P[0:3, 0:3] *= 10.0
    P[3:6, 3:6] *= 1.0
    P[6:9, 6:9] *= 0.5
    return x, P


# chi-squared 99% critical value for 2 degrees of freedom (GPS position is 2D) --
# a new GPS fix is rejected if its innovation is this statistically inconsistent
# with the filter's current belief, rather than trusted unconditionally.
GPS_INNOVATION_CHI2_THRESHOLD = 9.21

R_NHC_BASE = 0.10 ** 2
R_NHC_GROWTH = 0.02  # how fast NHC's confidence decays per second^2 of blackout -- tune if needed


def run_kf(a_world_, gyro_all_, yaw_, t_, dt_, gps_pos_, gps_available_,
           is_distinct_fix_, stationary_, Q_bias=50,
           speed_pred_lookup=None, r_speed_lookup=None, window=10):
    """
    Stage 4 filter. `speed_pred_lookup`/`r_speed_lookup` are optional -- pass
    them (from a trained speed model + covariance model, batch-predicted for
    the whole trip) to enable learned-speed fusion during blackout; omit
    them to run bias+ZUPT+NHC only.
    """
    N_ = len(t_)
    x, P = init_kf(gps_pos_[0])
    Q = np.zeros((9, 9))
    Q[0:3, 0:3] = np.eye(3) * 0.01
    Q[3:6, 3:6] = np.eye(3) * 0.1
    Q[6:9, 6:9] = np.eye(3) * Q_bias

    states = np.zeros((N_, 9))
    states[0] = x
    yaw_offset = 0.0
    yaw_offset_initialized = False
    time_in_blackout = 0.0
    n_gps_rejected = 0

    H_gps = np.zeros((2, 9)); H_gps[0, 0] = 1; H_gps[1, 1] = 1
    R_gps = np.diag([15.0 ** 2, 15.0 ** 2])
    H_zupt = np.zeros((3, 9)); H_zupt[0, 3] = 1; H_zupt[1, 4] = 1; H_zupt[2, 5] = 1
    R_zupt = np.eye(3) * 0.05 ** 2
    H_nhc = np.zeros((1, 9))
    MIN_SPEED_FOR_NHC = 1.0
    YAW_OFFSET_ALPHA = 0.02
    H_speed = np.zeros((1, 9))
    R_speed_default = np.array([[8.0 ** 2]])

    for k in range(1, N_):
        dt_k = dt_[k]
        c_off, s_off = np.cos(yaw_offset), np.sin(yaw_offset)
        a_map = np.array([
            a_world_[k, 0] * c_off - a_world_[k, 1] * s_off,
            a_world_[k, 0] * s_off + a_world_[k, 1] * c_off,
            a_world_[k, 2]
        ])
        bias = x[6:9]
        a_corr = a_map - bias
        x[0:3] += x[3:6] * dt_k + 0.5 * a_corr * dt_k ** 2
        x[3:6] += a_corr * dt_k

        F = np.eye(9)
        F[0:3, 3:6] = np.eye(3) * dt_k
        F[0:3, 6:9] = -0.5 * np.eye(3) * dt_k ** 2
        F[3:6, 6:9] = -np.eye(3) * dt_k
        P = F @ P @ F.T + Q
        speed = np.hypot(x[3], x[4])

        # --- GPS update: only on a genuinely new fix, AND only if statistically
        #     consistent with current belief (innovation/chi-squared gating) ---
        if gps_available_[k] and is_distinct_fix_[k]:
            z = gps_pos_[k]
            y = z - H_gps @ x
            S = H_gps @ P @ H_gps.T + R_gps
            mahalanobis_sq = float(y.T @ np.linalg.inv(S) @ y)

            if mahalanobis_sq <= GPS_INNOVATION_CHI2_THRESHOLD:
                K = P @ H_gps.T @ np.linalg.inv(S)
                x = x + K @ y
                P = (np.eye(9) - K @ H_gps) @ P
                time_in_blackout = 0.0

                if speed > MIN_SPEED_FOR_NHC:
                    map_heading = np.arctan2(x[4], x[3])
                    raw_offset = np.arctan2(np.sin(map_heading - yaw_[k]), np.cos(map_heading - yaw_[k]))
                    if not yaw_offset_initialized:
                        yaw_offset = raw_offset
                        yaw_offset_initialized = True
                    else:
                        yaw_offset = np.arctan2(
                            (1 - YAW_OFFSET_ALPHA) * np.sin(yaw_offset) + YAW_OFFSET_ALPHA * np.sin(raw_offset),
                            (1 - YAW_OFFSET_ALPHA) * np.cos(yaw_offset) + YAW_OFFSET_ALPHA * np.cos(raw_offset))
            else:
                # Fix rejected as an outlier -- don't reset time_in_blackout, don't
                # update yaw_offset from it, don't let it touch the state at all.
                n_gps_rejected += 1
        else:
            time_in_blackout += dt_k

        # --- ZUPT ---
        if stationary_[k]:
            y = np.zeros(3) - H_zupt @ x
            S = H_zupt @ P @ H_zupt.T + R_zupt
            K = P @ H_zupt.T @ np.linalg.inv(S)
            x = x + K @ y
            P = (np.eye(9) - K @ H_zupt) @ P

        # --- NHC, confidence-decayed with time spent in blackout ---
        if not stationary_[k] and speed > MIN_SPEED_FOR_NHC and yaw_offset_initialized:
            corrected_yaw = yaw_[k] + yaw_offset
            hx, hy = np.cos(corrected_yaw), np.sin(corrected_yaw)
            H_nhc[0, 3], H_nhc[0, 4] = -hy, hx
            R_nhc_current = np.array([[R_NHC_BASE + R_NHC_GROWTH * time_in_blackout ** 2]])
            y_n = np.array([0.0]) - H_nhc @ x
            S_n = H_nhc @ P @ H_nhc.T + R_nhc_current
            K_n = P @ H_nhc.T @ np.linalg.inv(S_n)
            x = x + (K_n @ y_n).flatten()
            P = (np.eye(9) - K_n @ H_nhc) @ P

        # --- optional learned-speed fusion, only during blackout ---
        if speed_pred_lookup is not None and not gps_available_[k] and not stationary_[k] and k >= window:
            pred_kmh = speed_pred_lookup[k]
            if not np.isnan(pred_kmh) and speed > 0.5:
                pred_ms = pred_kmh / 3.6
                heading = np.arctan2(x[4], x[3])
                H_speed[0, 3], H_speed[0, 4] = np.cos(heading), np.sin(heading)
                R_speed_k = R_speed_default
                if r_speed_lookup is not None and not np.isnan(r_speed_lookup[k]):
                    R_speed_k = np.array([[r_speed_lookup[k]]])
                y_s = np.array([pred_ms]) - H_speed @ x
                S_s = H_speed @ P @ H_speed.T + R_speed_k
                K_s = P @ H_speed.T @ np.linalg.inv(S_s)
                x = x + (K_s @ y_s).flatten()
                P = (np.eye(9) - K_s @ H_speed) @ P

        states[k] = x

    return states, n_gps_rejected


def compute_stationary_mask(a_world_, gyro_all_):
    acc_mag = np.sqrt((a_world_ ** 2).sum(axis=1))
    acc_var = pd.Series(acc_mag).rolling(10).var().fillna(10.0).values
    gyro_mag = np.sqrt((gyro_all_ ** 2).sum(axis=1))
    gyro_var = pd.Series(gyro_mag).rolling(10).var().fillna(10.0).values
    stationary = pd.Series(
        (acc_var < 3.0) & (gyro_var < 0.03) & (np.abs(acc_mag) < 0.5)
    ).rolling(10).min().fillna(0).astype(bool).values
    return stationary


# ---------------------------------------------------------------------------
# Stage 7: endpoint-anchored map-matching through the GPS blackout
# ---------------------------------------------------------------------------

_graph_cache = {}


def _get_cached_graph(bbox, network_type, precision=3):
    import osmnx as ox
    key = (round(bbox[0], precision), round(bbox[1], precision),
           round(bbox[2], precision), round(bbox[3], precision), network_type)
    if key in _graph_cache:
        return _graph_cache[key]

    G, last_err = None, None
    for attempt in range(3):
        try:
            time.sleep(2.0 * (attempt + 1))
            G_try = ox.graph_from_bbox(bbox, network_type=network_type, simplify=False)
            if len(G_try.nodes) > 0:
                G = G_try
                break
        except Exception as e:
            last_err = str(e)
            continue
    _graph_cache[key] = (G, last_err)
    return G, last_err


def _local_to_latlon(x, y, lat0_, lon0_):
    return lat0_ + y / 111320, lon0_ + x / (111320 * np.cos(np.radians(lat0_)))


def map_match_segment(states, gps_pos_, gps_available_, stationary_, t_, lat_, lon_, lat0_, lon0_, margin=0.01):
    """
    Endpoint-anchored recalibration: routes between the last known-good GPS
    fix before blackout and the first one after, via OSM road geometry, then
    places position along that route using the KF's own speed profile
    (ZUPT-enforced to exactly zero at detected stops).

    This is RETROSPECTIVE -- it needs the post-blackout GPS fix to know
    where the route led, so it cannot run live during the blackout itself.
    Live on-screen tracking during blackout is Stage 4's real-time output;
    this recalibrates the just-completed segment the instant GPS reacquires
    (a standard "smooth live, correct retrospectively" pattern).

    Returns (matched_states, status_string). Never silently fails -- a
    failure reason is always returned, not swallowed.
    """
    from shapely.geometry import LineString
    from shapely.ops import linemerge
    import osmnx as ox

    matched = states.copy()
    blackout_idx = np.where(~gps_available_)[0]
    pre_idx = np.where(gps_available_)[0]
    if len(blackout_idx) == 0 or len(pre_idx) == 0:
        return matched, "no blackout window"
    pre_c = pre_idx[pre_idx < blackout_idx[0]]
    post_c = pre_idx[pre_idx > blackout_idx[-1]]
    if len(pre_c) == 0 or len(post_c) == 0:
        return matched, "blackout touches trip boundary -- no pre/post GPS anchor"
    pre_i, post_i = pre_c[-1], post_c[0]

    bbox = (lon_.min() - margin, lat_.min() - margin, lon_.max() + margin, lat_.max() + margin)
    G, last_err, used_fallback = None, None, False
    for network_type in ["drive", "all"]:
        G, last_err = _get_cached_graph(bbox, network_type)
        if G is not None:
            used_fallback = (network_type == "all")
            break
    if G is None:
        return matched, f"graph fetch failed for both network types: {last_err}"

    true_lat, true_lon = _local_to_latlon(gps_pos_[:, 0], gps_pos_[:, 1], lat0_, lon0_)
    try:
        start_node = ox.distance.nearest_nodes(G, true_lon[pre_i], true_lat[pre_i])
        end_node = ox.distance.nearest_nodes(G, true_lon[post_i], true_lat[post_i])
        route_nodes = ox.shortest_path(G, start_node, end_node, weight="length")
    except Exception as e:
        return matched, f"routing failed: {e}"

    if route_nodes is None or len(route_nodes) < 2:
        if margin < 0.05:
            return map_match_segment(states, gps_pos_, gps_available_, stationary_, t_,
                                      lat_, lon_, lat0_, lon0_, margin=margin * 3)
        return matched, "no route found (even with wider margin)"

    edge_lines = []
    for u, v in zip(route_nodes[:-1], route_nodes[1:]):
        if G.has_edge(u, v):
            data = min(G.get_edge_data(u, v).values(), key=lambda d: d.get("length", 1e9))
        elif G.has_edge(v, u):
            data = min(G.get_edge_data(v, u).values(), key=lambda d: d.get("length", 1e9))
        else:
            continue
        geom = data.get("geometry") or LineString(
            [(G.nodes[u]["x"], G.nodes[u]["y"]), (G.nodes[v]["x"], G.nodes[v]["y"])])
        edge_lines.append(geom)
    if not edge_lines:
        return matched, "route had no usable edge geometry"

    route_line = linemerge(edge_lines) if len(edge_lines) > 1 else edge_lines[0]
    if route_line.geom_type == "MultiLineString":
        route_line = max(route_line.geoms, key=lambda g: g.length)

    kf_speed = np.hypot(states[:, 3], states[:, 4]).copy()
    kf_speed[stationary_] = 0.0
    seg_idx = np.arange(pre_i, post_i + 1)
    dt_seg = np.diff(t_[seg_idx], prepend=t_[seg_idx[0]])
    cum = np.cumsum(kf_speed[seg_idx] * dt_seg)
    frac = cum / cum[-1] if cum[-1] > 0 else np.linspace(0, 1, len(seg_idx))

    recal_lat, recal_lon = [], []
    for f_ in frac:
        pt = route_line.interpolate(f_ * route_line.length)
        recal_lon.append(pt.x)
        recal_lat.append(pt.y)
    recal_lat, recal_lon = np.array(recal_lat), np.array(recal_lon)

    matched[seg_idx, 0] = (recal_lon - lon0_) * 111320 * np.cos(np.radians(lat0_))
    matched[seg_idx, 1] = (recal_lat - lat0_) * 111320

    status = "success" + (" (used network_type='all' fallback)" if used_fallback else "")
    return matched, status


# ---------------------------------------------------------------------------
# Top-level entry point: what the app calls
# ---------------------------------------------------------------------------

def estimate_position(csv_path, blackout_frac=None, blackout_start=None, blackout_end=None,
                       Q_bias=50, max_dt_s=5.0):
    """
    Single entry point for the app team.

    Input:  path to a raw trip CSV (same sensor schema as the IO-VNBD trips
            this was benchmarked on -- accelerometer/gravity/gyroscope/
            orientation columns + intermittent GPS lat/lon).
    Output: (states, matched_states, status) where `states`/`matched_states`
            are (N, 9) arrays whose first two columns are [x, y] position in
            meters relative to the trip's start point, and `status` is the
            Stage 7 outcome string (check it -- a non-"success" status means
            `matched_states` is just Stage 4's output, unrecalibrated).
            `matched_states` is the one to display/log once available.

    gps_available should reflect real GPS dropout (e.g. from signal quality/
    fix-age in the live app), not a simulated blackout window -- the
    blackout_frac/start/end args here exist only for offline benchmarking
    against IO-VNBD trips where blackout has to be synthetically inserted.
    """
    df = load_and_process_trip(csv_path)
    t = df["t_align"].values
    dt = np.diff(t, prepend=t[0])
    bad_dt_mask = (dt < 0) | (dt > max_dt_s)
    if bad_dt_mask.any():
        df = df.loc[~bad_dt_mask].reset_index(drop=True)
        t = df["t_align"].values
        dt = np.diff(t, prepend=t[0])

    lat0, lon0 = df["lat"].values[0], df["lon"].values[0]
    gx = (df["lon"].values - lon0) * 111320 * np.cos(np.radians(lat0))
    gy = (df["lat"].values - lat0) * 111320
    gps_pos = np.column_stack([gx, gy])
    gps_diff = np.diff(gps_pos, axis=0, prepend=gps_pos[0:1])
    is_distinct_fix = np.abs(gps_diff).sum(axis=1) > 1e-6

    if blackout_start is not None and blackout_end is not None:
        gps_available = ~((t >= blackout_start) & (t <= blackout_end))
    elif blackout_frac is not None:
        bo_start = blackout_frac * t[-1]
        bo_end = bo_start + 30.0
        gps_available = ~((t >= bo_start) & (t <= bo_end))
    else:
        gps_available = np.ones(len(t), dtype=bool)  # replace with real live-GPS-availability signal

    a_world = df[["acc_forward", "acc_lateral", "acc_vertical"]].to_numpy()
    gyro_all = df[["gyro_yaw", "gyro_pitch", "gyro_roll"]].to_numpy()
    yaw = np.unwrap(np.radians(90.0 - df["orientation_yaw_raw"].values))
    stationary = compute_stationary_mask(a_world, gyro_all)

    states4, n_rejected = run_kf(a_world, gyro_all, yaw, t, dt, gps_pos, gps_available,
                                  is_distinct_fix, stationary, Q_bias=Q_bias)
    if n_rejected > 0:
        print(f"[estimate_position] rejected {n_rejected} GPS fix(es) as statistical outliers")

    matched, status = map_match_segment(states4, gps_pos, gps_available, stationary, t,
                                         df["lat"].values, df["lon"].values, lat0, lon0)

    return states4, matched, status


if __name__ == "__main__":
    import sys
    if len(sys.argv) < 2:
        print("Usage: python dead_reckoning_pipeline.py <trip_csv_path>")
        sys.exit(1)
    states4, matched, status = estimate_position(sys.argv[1], blackout_frac=0.3)
    print("Stage 7 status:", status)
    print("Final position (Stage 4):", states4[-1, 0:2])
    print("Final position (Stage 7, map-matched):", matched[-1, 0:2])
