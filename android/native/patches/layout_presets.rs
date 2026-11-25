use axum::{
    body::{Body, Bytes},
    extract::Extension,
    http::{Response, StatusCode},
    response::IntoResponse,
};
use std::path::PathBuf;

use crate::infrastructure::config::Config;

fn presets_path(config: &Config) -> PathBuf {
    // Stored next to the database/config in TANOSHI_HOME (config.path is
    // private, but the database path lives in the same directory).
    PathBuf::from(&config.database_path)
        .parent()
        .map(|parent| parent.join("layout_presets.json"))
        .unwrap_or_else(|| PathBuf::from("layout_presets.json"))
}

fn json_response(status: StatusCode, body: String) -> Response<Body> {
    Response::builder()
        .status(status)
        .header("content-type", "application/json")
        .header("cache-control", "no-store")
        .body(Body::from(body))
        .unwrap_or_else(|_| Response::new(Body::empty()))
}

/// Shared layout presets, stored next to the server config so every device
/// connected to the same server sees the same list.
pub async fn get_layout_presets(Extension(config): Extension<Config>) -> impl IntoResponse {
    let path = presets_path(&config);
    match std::fs::read(&path) {
        Ok(bytes) => match serde_json::from_slice::<serde_json::Value>(&bytes) {
            Ok(value) => json_response(StatusCode::OK, value.to_string()),
            Err(error) => {
                log::warn!("invalid layout_presets.json ({error}); returning an empty list");
                json_response(StatusCode::OK, r#"{"presets":[]}"#.to_string())
            }
        },
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            json_response(StatusCode::OK, r#"{"presets":[]}"#.to_string())
        }
        Err(error) => {
            log::warn!("could not read layout_presets.json: {error}");
            json_response(StatusCode::INTERNAL_SERVER_ERROR, r#"{"presets":[]}"#.to_string())
        }
    }
}

/// Replace the shared preset list. The body is validated as JSON and written
/// atomically so a crashed write cannot corrupt the list.
pub async fn put_layout_presets(
    Extension(config): Extension<Config>,
    body: Bytes,
) -> impl IntoResponse {
    let value = match serde_json::from_slice::<serde_json::Value>(&body) {
        Ok(value @ serde_json::Value::Object(_)) => value,
        Ok(_) => return json_response(StatusCode::BAD_REQUEST, r#"{"error":"expected an object"}"#.to_string()),
        Err(error) => {
            return json_response(
                StatusCode::BAD_REQUEST,
                format!(r#"{{"error":"invalid json: {error}"}}"#),
            )
        }
    };
    if !value.get("presets").is_some_and(|presets| presets.is_array()) {
        return json_response(
            StatusCode::BAD_REQUEST,
            r#"{"error":"presets must be an array"}"#.to_string(),
        );
    }

    let path = presets_path(&config);
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let tmp = path.with_extension("json.tmp");
    let serialized = value.to_string();
    if let Err(error) = std::fs::write(&tmp, &serialized) {
        log::warn!("could not write {}: {error}", tmp.display());
        return json_response(StatusCode::INTERNAL_SERVER_ERROR, r#"{"error":"write failed"}"#.to_string());
    }
    if let Err(error) = std::fs::rename(&tmp, &path) {
        log::warn!("could not replace {}: {error}", path.display());
        return json_response(StatusCode::INTERNAL_SERVER_ERROR, r#"{"error":"write failed"}"#.to_string());
    }

    json_response(StatusCode::OK, serialized)
}
