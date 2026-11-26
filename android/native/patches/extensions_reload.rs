use axum::{
    body::Body,
    extract::Extension,
    http::{Response, StatusCode},
    response::IntoResponse,
};
use tanoshi_vm::extension::ExtensionManager;

fn json_response(status: StatusCode, body: String) -> Response<Body> {
    Response::builder()
        .status(status)
        .header("content-type", "application/json")
        .header("cache-control", "no-store")
        .body(Body::from(body))
        .unwrap_or_else(|_| Response::new(Body::empty()))
}

/// Re-scan the extension directories and reload every plugin in the running
/// server. The Android app calls this after installing/removing/enabling a
/// source so a full server restart is no longer required.
pub async fn reload_extensions(
    Extension(manager): Extension<ExtensionManager>,
) -> impl IntoResponse {
    match manager.reload_all().await {
        Ok(()) => {
            let sources = manager.list().await.map(|sources| sources.len()).unwrap_or(0);
            log::info!("extensions hot-reloaded; {sources} source(s) available");
            json_response(
                StatusCode::OK,
                format!(r#"{{"ok":true,"sources":{sources}}}"#),
            )
        }
        Err(error) => {
            log::error!("could not reload extensions: {error}");
            json_response(
                StatusCode::INTERNAL_SERVER_ERROR,
                format!(r#"{{"ok":false,"error":"{error}"}}"#),
            )
        }
    }
}
