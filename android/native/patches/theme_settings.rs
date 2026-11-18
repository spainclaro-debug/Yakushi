//! Per-device web-UI theme palette.
//!
//! The selected palette (a preset or a custom primary/secondary/accent mix)
//! is stored in `localStorage` only, so it applies to the browser/device the
//! user chose it on. Other devices keep their own selection and fall back to
//! the stock Auto/Light/Dark appearance until someone picks a palette there.
//!
//! Preset colors are inspired by the combinations in
//! <https://www.inspoai.io/blogs/best-color-combinations-for-mobile-apps>;
//! the custom picker offers the project's fixed color swatches.

use dominator::{clone, events, html, Dom};
use futures_signals::{
    map_ref,
    signal::{Mutable, SignalExt},
};
use serde::{Deserialize, Serialize};
use std::rc::Rc;
use wasm_bindgen::JsCast;
use web_sys::HtmlElement;

use crate::common::snackbar;
use crate::utils::{apply_theme, apply_theme_color, body, document, local_storage};

pub const STORAGE_KEY: &str = "settings:theme-palette";

pub struct ThemePreset {
    pub name: &'static str,
    pub primary: &'static str,
    pub secondary: &'static str,
    pub accent: &'static str,
}

/// Preset themes shown in Settings -> Appearance -> Themes.
pub const PRESETS: &[ThemePreset] = &[
    ThemePreset {
        name: "Monochromatic Blue",
        primary: "#1565c0",
        secondary: "#64b5f6",
        accent: "#0d47a1",
    },
    ThemePreset {
        name: "Complementary Orange",
        primary: "#1e88e5",
        secondary: "#fb8c00",
        accent: "#ff6d00",
    },
    ThemePreset {
        name: "Triadic Yellow + Teal + Magenta",
        primary: "#f9a825",
        secondary: "#00897b",
        accent: "#c2185b",
    },
    ThemePreset {
        name: "Analogous Green + Blue + Teal",
        primary: "#43a047",
        secondary: "#1e88e5",
        accent: "#00897b",
    },
    ThemePreset {
        name: "High Contrast Black + Yellow + White",
        primary: "#ffeb3b",
        secondary: "#000000",
        accent: "#ffffff",
    },
    ThemePreset {
        name: "Google Material Blue",
        primary: "#4285f4",
        secondary: "#34a853",
        accent: "#ea4335",
    },
    ThemePreset {
        name: "Airbnb Coral",
        primary: "#ff5a5f",
        secondary: "#00a699",
        accent: "#fc642d",
    },
    ThemePreset {
        name: "Wise Mint",
        primary: "#9fe870",
        secondary: "#163300",
        accent: "#0e0f0c",
    },
    ThemePreset {
        name: "Duolingo Amber",
        primary: "#ffc800",
        secondary: "#58cc02",
        accent: "#1cb0f6",
    },
    ThemePreset {
        name: "Headspace Warmth",
        primary: "#ff7e1d",
        secondary: "#ffb74d",
        accent: "#2e2a24",
    },
    ThemePreset {
        name: "Slack Aubergine",
        primary: "#4a154b",
        secondary: "#36c5f0",
        accent: "#ecb22e",
    },
    ThemePreset {
        name: "Pinterest Cherry",
        primary: "#e60023",
        secondary: "#111111",
        accent: "#ffffff",
    },
];

pub const PRIMARY_COLORS: &[&str] = &[
    "#e11d1d", "#fdff35", "#0eacf9", "#76f612", "#6c0bd2", "#134c67", "#7cb2cc", "#db553e",
    "#ecca60", "#636363", "#0060aa", "#ffed10", "#e20025", "#010101", "#ffffff", "#0033ff",
    "#ff0000", "#ffa500", "#009900", "#aaaaaa",
];

pub const SECONDARY_COLORS: &[&str] = &[
    "#f1f58a", "#bfd76f", "#8869b7", "#1fbed6", "#ff717e", "#07d2c3", "#9e7be3", "#fb5da5",
    "#ff9a34", "#6891ff", "#605ca8", "#d81b60", "#ff852b", "#39cccc", "#3d9970", "#d8d2c2",
    "#fafeff", "#444237", "#bbbbbb", "#aaaaaa",
];

pub const ACCENT_COLORS: &[&str] = &[
    "#2d2b2a", "#565554", "#9c9282", "#40303f", "#2b3744", "#bf2525", "#ed7d31", "#ffc000",
    "#69b647", "#3d85c6", "#007acc", "#d4d4d4", "#333333", "#252525", "#1e1e1e", "#ff7cb8",
    "#daf1ff", "#ffffff", "#a1969f", "#6f5e68",
];

#[derive(Clone, Serialize, Deserialize)]
pub struct StoredTheme {
    pub kind: String,
    pub name: String,
    pub primary: String,
    pub secondary: String,
    pub accent: String,
}

fn is_hex(value: &str) -> bool {
    value.len() == 7
        && value.starts_with('#')
        && value[1..].chars().all(|c| c.is_ascii_hexdigit())
}

fn parse_hex(hex: &str) -> Option<(u8, u8, u8)> {
    if !is_hex(hex) {
        return None;
    }
    let r = u8::from_str_radix(&hex[1..3], 16).ok()?;
    let g = u8::from_str_radix(&hex[3..5], 16).ok()?;
    let b = u8::from_str_radix(&hex[5..7], 16).ok()?;
    Some((r, g, b))
}

fn channel_luminance(channel: u8) -> f32 {
    let c = channel as f32 / 255.0;
    if c <= 0.03928 {
        c / 12.92
    } else {
        ((c + 0.055) / 1.055).powf(2.4)
    }
}

fn luminance(hex: &str) -> f32 {
    match parse_hex(hex) {
        Some((r, g, b)) => {
            0.2126 * channel_luminance(r)
                + 0.7152 * channel_luminance(g)
                + 0.0722 * channel_luminance(b)
        }
        None => 0.2,
    }
}

/// Black or white, whichever contrasts best with the given background.
fn contrast_text(hex: &str) -> &'static str {
    if luminance(hex) > 0.42 {
        "#111111"
    } else {
        "#ffffff"
    }
}

fn lighten(hex: &str, amount: f32) -> String {
    match parse_hex(hex) {
        Some((r, g, b)) => {
            let mix = |channel: u8| -> u8 {
                (channel as f32 + (255.0 - channel as f32) * amount)
                    .round()
                    .clamp(0.0, 255.0) as u8
            };
            format!("#{:02x}{:02x}{:02x}", mix(r), mix(g), mix(b))
        }
        None => hex.to_string(),
    }
}

fn root() -> Option<HtmlElement> {
    document()
        .document_element()
        .and_then(|element| element.dyn_into::<HtmlElement>().ok())
}

fn set_var(name: &str, value: &str) {
    if let Some(root) = root() {
        let _ = root.style().set_property(name, value);
    }
}

fn remove_var(name: &str) {
    if let Some(root) = root() {
        let _ = root.style().remove_property(name);
    }
}

const THEME_VARS: &[&str] = &[
    "--primary-color",
    "--primary-color-300",
    "--secondary-color",
    "--accent-color",
    "--topbar-background-color",
    "--topbar-text-color",
    "--topbar-button-color",
    "--topbar-bottom-border",
    "--theme-text-light",
    "--theme-text-dark",
    "--theme-title-light",
    "--theme-title-dark",
    "--theme-title-on-image",
    "--heading-on-primary",
    "--bottombar-text-color",
    "--bottombar-active-color",
    "--panel-text",
    "--unread-text",
    "--theme-cell-light",
    "--theme-cell-dark",
    "--theme-cell-text-light",
    "--theme-cell-text-dark",
];

fn blend(from: &str, to: &str, amount: f32) -> String {
    match (parse_hex(from), parse_hex(to)) {
        (Some((fr, fg, fb)), Some((tr, tg, tb))) => {
            let mix = |a: u8, b: u8| -> u8 {
                (a as f32 * (1.0 - amount) + b as f32 * amount)
                    .round()
                    .clamp(0.0, 255.0) as u8
            };
            format!("#{:02x}{:02x}{:02x}", mix(fr, tr), mix(fg, tg), mix(fb, tb))
        }
        _ => from.to_string(),
    }
}

fn contrast_ratio(a: &str, b: &str) -> f32 {
    let la = luminance(a) + 0.05;
    let lb = luminance(b) + 0.05;
    if la > lb {
        la / lb
    } else {
        lb / la
    }
}

/// Keep `color` recognisable but readable on `background` (WCAG-ish 3:1).
fn ensure_contrast(color: &str, background: &str, minimum: f32) -> String {
    if contrast_ratio(color, background) >= minimum {
        return color.to_string();
    }
    let target = if luminance(background) > 0.42 {
        "#000000"
    } else {
        "#ffffff"
    };
    for step in 1..=10 {
        let candidate = blend(color, target, step as f32 / 10.0);
        if contrast_ratio(&candidate, background) >= minimum {
            return candidate;
        }
    }
    target.to_string()
}

/// Apply a palette to the current document (this device only).
///
/// Both light and dark mode get their own adaptive text/heading colors, so
/// switching the appearance mode keeps the themed UI readable without
/// re-applying the palette.
pub fn apply_palette(primary: &str, secondary: &str, accent: &str) {
    set_var("--primary-color", primary);
    set_var("--primary-color-300", &lighten(primary, 0.35));
    set_var("--secondary-color", secondary);
    set_var("--accent-color", accent);

    // Top/bottom banners use the primary color in both modes.
    set_var("--topbar-background-color", primary);
    set_var("--bottombar-background-color", primary);
    let on_primary = contrast_text(primary);
    set_var("--topbar-text-color", on_primary);
    set_var("--topbar-button-color", on_primary);
    set_var("--bottombar-text-color", on_primary);
    set_var("--topbar-bottom-border", &format!("2px solid {accent}"));
    set_var("--heading-on-primary", &ensure_contrast(accent, primary, 3.0));
    set_var(
        "--bottombar-active-color",
        &ensure_contrast(accent, primary, 3.0),
    );

    // The page background is the accent/neutral color at 30% opacity over the
    // mode's base color (the color-mix rules live in styles.scss); text must
    // adapt to the resulting tint.
    let light_background = blend("#f4f4f4", accent, 0.3);
    let dark_background = blend("#0a0a0a", accent, 0.3);
    set_var("--theme-text-light", contrast_text(&light_background));
    set_var("--theme-text-dark", contrast_text(&dark_background));
    set_var(
        "--theme-title-light",
        &ensure_contrast(accent, &light_background, 3.0),
    );
    set_var(
        "--theme-title-dark",
        &ensure_contrast(accent, &dark_background, 3.0),
    );
    // Cover titles sit on the dark thumbnail gradient, not on the page tint.
    set_var(
        "--theme-title-on-image",
        &ensure_contrast(accent, "#101010", 3.0),
    );

    // Settings panels use the accent color directly; the list cells inside the
    // settings pages need their own themed surface because the stock
    // --list-group-background-color resolves at :root and would stay white.
    set_var("--panel-text", contrast_text(accent));
    let cell_light = blend("#ffffff", accent, 0.22);
    let cell_dark = blend("#111111", accent, 0.30);
    set_var("--theme-cell-light", &cell_light);
    set_var("--theme-cell-dark", &cell_dark);
    set_var("--theme-cell-text-light", contrast_text(&cell_light));
    set_var("--theme-cell-text-dark", contrast_text(&cell_dark));
    // Unread-chapter badges use the secondary color in both modes.
    set_var("--unread-text", contrast_text(secondary));

    body().class_list().add_1("themed").ok();
    let _ = apply_theme_color(primary);
}

/// Re-apply the stored palette on page load (called before the app renders).
pub fn apply_stored_theme() {
    if let Ok(Some(raw)) = local_storage().get_item(STORAGE_KEY) {
        if let Ok(theme) = serde_json::from_str::<StoredTheme>(&raw) {
            if is_hex(&theme.primary) && is_hex(&theme.secondary) && is_hex(&theme.accent) {
                apply_palette(&theme.primary, &theme.secondary, &theme.accent);
            }
        }
    }
}

fn persist(theme: &StoredTheme) {
    if let Ok(raw) = serde_json::to_string(theme) {
        let _ = local_storage().set_item(STORAGE_KEY, &raw);
    }
}

pub struct ThemeSettings {
    selected: Mutable<usize>,
    custom_mode: Mutable<bool>,
    new_theme_open: Mutable<bool>,
    custom_primary: Mutable<String>,
    custom_secondary: Mutable<String>,
    custom_accent: Mutable<String>,
    applied: Mutable<String>,
}

impl ThemeSettings {
    pub fn new() -> Rc<Self> {
        let mut selected = 0usize;
        let mut custom_mode = false;
        let mut custom_primary = PRIMARY_COLORS[0].to_string();
        let mut custom_secondary = SECONDARY_COLORS[0].to_string();
        let mut custom_accent = ACCENT_COLORS[0].to_string();
        let mut applied = "Default (Auto)".to_string();

        if let Ok(Some(raw)) = local_storage().get_item(STORAGE_KEY) {
            if let Ok(theme) = serde_json::from_str::<StoredTheme>(&raw) {
                if is_hex(&theme.primary) && is_hex(&theme.secondary) && is_hex(&theme.accent) {
                    custom_primary = theme.primary.clone();
                    custom_secondary = theme.secondary.clone();
                    custom_accent = theme.accent.clone();
                    if theme.kind == "custom" {
                        custom_mode = true;
                        applied = "Custom theme".to_string();
                    } else if let Some(index) =
                        PRESETS.iter().position(|preset| preset.name == theme.name)
                    {
                        selected = index;
                        applied = theme.name;
                    } else {
                        custom_mode = true;
                        applied = "Custom theme".to_string();
                    }
                }
            }
        }

        Rc::new(Self {
            selected: Mutable::new(selected),
            custom_mode: Mutable::new(custom_mode),
            new_theme_open: Mutable::new(false),
            custom_primary: Mutable::new(custom_primary),
            custom_secondary: Mutable::new(custom_secondary),
            custom_accent: Mutable::new(custom_accent),
            applied: Mutable::new(applied),
        })
    }

    fn apply(&self) {
        let (kind, name, primary, secondary, accent) = if self.custom_mode.get() {
            (
                "custom",
                "Custom theme".to_string(),
                self.custom_primary.get_cloned(),
                self.custom_secondary.get_cloned(),
                self.custom_accent.get_cloned(),
            )
        } else {
            let preset = &PRESETS[self.selected.get()];
            (
                "preset",
                preset.name.to_string(),
                preset.primary.to_string(),
                preset.secondary.to_string(),
                preset.accent.to_string(),
            )
        };

        if !is_hex(&primary) || !is_hex(&secondary) || !is_hex(&accent) {
            snackbar::show("Pick a valid color first".to_string());
            return;
        }

        apply_palette(&primary, &secondary, &accent);
        persist(&StoredTheme {
            kind: kind.to_string(),
            name: name.clone(),
            primary,
            secondary,
            accent,
        });
        self.applied.set(name.clone());
        snackbar::show(format!("Theme \"{name}\" applied on this device"));
    }

    fn reset(&self) {
        for var in THEME_VARS {
            remove_var(var);
        }
        let _ = body().class_list().remove_1("themed");
        let _ = local_storage().remove_item(STORAGE_KEY);
        apply_theme(local_storage().get("theme").ok().flatten());
        self.applied.set("Default (Auto)".to_string());
        self.custom_mode.set(false);
        self.new_theme_open.set(false);
        snackbar::show("Theme reset to default for this device".to_string());
    }

    fn render_color_group(
        label: &'static str,
        colors: &'static [&'static str],
        value: Mutable<String>,
        settings: Rc<Self>,
    ) -> Dom {
        let buttons: Vec<Dom> = colors
            .iter()
            .map(|color| {
                let color = *color;
                let selected_signal = value.signal_cloned();
                let value = value.clone();
                let settings = settings.clone();
                html!("button", {
                    .class("theme-color-swatch")
                    .attr("title", color)
                    .style("background-color", color)
                    .class_signal("active", selected_signal.map(move |current| current == color))
                    .event(clone!(value, settings => move |_: events::Click| {
                        value.set(color.to_string());
                        settings.custom_mode.set(true);
                    }))
                })
            })
            .collect();

        html!("div", {
            .children(&mut [
                html!("label", {
                    .style("margin", "0.5rem")
                    .text(label)
                }),
                html!("div", {
                    .class("theme-colors")
                    .children(&mut { buttons })
                }),
            ])
        })
    }

    fn render_custom_panel(settings: Rc<Self>) -> Dom {
        html!("div", {
            .style("margin", "0.5rem 0")
            .children(&mut [
                html!("p", {
                    .style("margin", "0.25rem 0.5rem")
                    .style("font-size", "0.85em")
                    .style("opacity", "0.8")
                    .text("Pick a primary, secondary and accent color, then press Apply.")
                }),
                Self::render_color_group(
                    "Primary",
                    PRIMARY_COLORS,
                    settings.custom_primary.clone(),
                    settings.clone(),
                ),
                Self::render_color_group(
                    "Secondary",
                    SECONDARY_COLORS,
                    settings.custom_secondary.clone(),
                    settings.clone(),
                ),
                Self::render_color_group(
                    "Accent",
                    ACCENT_COLORS,
                    settings.custom_accent.clone(),
                    settings.clone(),
                ),
            ])
        })
    }

    pub fn render(self: Rc<Self>) -> Dom {
        let settings = self;

        let preset_rows: Vec<Dom> = PRESETS
            .iter()
            .enumerate()
            .map(|(index, preset)| {
                let active_settings = settings.clone();
                let click_settings = settings.clone();
                html!("div", {
                    .class("theme-preset-row")
                    .class_signal("active", map_ref! {
                        let selected = active_settings.selected.signal_cloned(),
                        let custom = active_settings.custom_mode.signal_cloned() =>
                        !*custom && *selected == index
                    })
                    .event(clone!(click_settings => move |_: events::Click| {
                        click_settings.selected.set(index);
                        click_settings.custom_mode.set(false);
                        click_settings.new_theme_open.set(false);
                    }))
                    .children(&mut [
                        html!("span", {
                            .class("theme-swatch")
                            .style("background-color", preset.primary)
                        }),
                        html!("span", {
                            .class("theme-swatch")
                            .style("background-color", preset.secondary)
                        }),
                        html!("span", {
                            .class("theme-swatch")
                            .style("background-color", preset.accent)
                        }),
                        html!("span", {
                            .class("theme-preset-name")
                            .text(preset.name)
                        }),
                    ])
                })
            })
            .collect();

        html!("div", {
            .class("reader-settings")
            .class("non-modal")
            .children(&mut [
                html!("label", {
                    .style("margin", "0.5rem")
                    .text("Themes")
                }),
                html!("p", {
                    .style("margin", "0 0.5rem 0.25rem 0.5rem")
                    .style("font-size", "0.85em")
                    .style("opacity", "0.8")
                    .text("Applies to this browser/device only. Other devices keep their own theme (Auto by default).")
                }),
                html!("div", {
                    .style("margin", "0.25rem 0.5rem")
                    .style("font-size", "0.85em")
                    .text_signal(settings.applied.signal_cloned().map(|applied| format!("Applied: {applied}")))
                }),
                html!("div", {
                    .class("theme-preset-list")
                    .children(&mut { preset_rows })
                }),
                html!("button", {
                    .class("theme-new-btn")
                    .text("New theme")
                    .event(clone!(settings => move |_: events::Click| {
                        settings.new_theme_open.set_neq(!settings.new_theme_open.get());
                    }))
                }),
                html!("div", {
                    .child_signal(settings.new_theme_open.signal_cloned().map(clone!(settings => move |open| {
                        open.then(|| Self::render_custom_panel(settings.clone()))
                    })))
                }),
                html!("div", {
                    .class("theme-actions")
                    .children(&mut [
                        html!("button", {
                            .class("action-button")
                            .text("Apply")
                            .event(clone!(settings => move |_: events::Click| {
                                settings.apply();
                            }))
                        }),
                        html!("button", {
                            .class("action-button")
                            .text("Reset to default")
                            .event(clone!(settings => move |_: events::Click| {
                                settings.reset();
                            }))
                        }),
                    ])
                }),
            ])
        })
    }
}
