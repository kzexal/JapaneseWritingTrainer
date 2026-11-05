//! JNI bridge for the upstream FSRS Rust scheduler (FSRS 6.6.2).
//! The Android app owns learning/relearning policy; this bridge only computes
//! FSRS memory states and review intervals.

use fsrs::{FSRS, MemoryState};
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize)]
struct ReviewInput {
    stability: Option<f32>,
    difficulty: Option<f32>,
    elapsed_days: u32,
    desired_retention: f32,
}

#[derive(Debug, Serialize)]
struct StateOutput {
    stability: f32,
    difficulty: f32,
    interval_days: f32,
}

#[derive(Debug, Serialize)]
struct ReviewOutput {
    again: StateOutput,
    hard: StateOutput,
    good: StateOutput,
    easy: StateOutput,
}

fn state(value: fsrs::ItemState) -> StateOutput {
    StateOutput {
        stability: value.memory.stability,
        difficulty: value.memory.difficulty,
        interval_days: value.interval,
    }
}

fn calculate(input: ReviewInput) -> Result<String, String> {
    if !(0.01..0.999).contains(&input.desired_retention) {
        return Err("desired_retention must be in 0.01..0.999".into());
    }
    let previous = match (input.stability, input.difficulty) {
        (Some(stability), Some(difficulty)) if stability > 0.0 && difficulty > 0.0 => {
            Some(MemoryState { stability, difficulty })
        }
        (None, None) => None,
        _ => return Err("stability and difficulty must both be set or both be null".into()),
    };

    let states = FSRS::default()
        .next_states(previous, input.desired_retention, input.elapsed_days)
        .map_err(|e| e.to_string())?;
    serde_json::to_string(&ReviewOutput {
        again: state(states.again),
        hard: state(states.hard),
        good: state(states.good),
        easy: state(states.easy),
    }).map_err(|e| e.to_string())
}

/// Kotlin declaration: private external fun nativeReview(inputJson: String): String
#[no_mangle]
pub extern "system" fn Java_com_example_japanesewritingtrainer_UpstreamFsrsNative_nativeReview(
    mut env: JNIEnv,
    _class: JClass,
    input_json: JString,
) -> jstring {
    let result = (|| {
        let input: String = env.get_string(&input_json).map_err(|e| e.to_string())?.into();
        let parsed: ReviewInput = serde_json::from_str(&input).map_err(|e| e.to_string())?;
        calculate(parsed)
    })();

    let output = match result {
        Ok(json) => json,
        Err(message) => serde_json::json!({ "error": message }).to_string(),
    };
    env.new_string(output).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn first_review_returns_four_states() {
        let output = calculate(ReviewInput {
            stability: None,
            difficulty: None,
            elapsed_days: 0,
            desired_retention: 0.9,
        }).unwrap();
        let value: serde_json::Value = serde_json::from_str(&output).unwrap();
        for rating in ["again", "hard", "good", "easy"] {
            assert!(value[rating]["stability"].as_f64().unwrap() > 0.0);
            assert!(value[rating]["difficulty"].as_f64().unwrap() > 0.0);
            assert!(value[rating]["interval_days"].as_f64().unwrap() >= 0.0);
        }
    }

    #[test]
    fn previous_state_requires_both_values() {
        let result = calculate(ReviewInput {
            stability: Some(2.0),
            difficulty: None,
            elapsed_days: 1,
            desired_retention: 0.9,
        });
        assert!(result.is_err());
    }
}
