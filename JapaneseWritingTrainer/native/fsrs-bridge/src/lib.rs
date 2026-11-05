//! JNI bridge for upstream FSRS-rs 6.6.2.
//! The Android app retains learning/relearning policy and queue management.

use fsrs::{FSRS, MemoryState};
use jni::objects::{JObject, JString};
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
#[derive(Debug, Serialize, PartialEq)]
struct StateOutput { stability: f32, difficulty: f32, interval_days: f32 }
#[derive(Debug, Serialize, PartialEq)]
struct ReviewOutput { again: StateOutput, hard: StateOutput, good: StateOutput, easy: StateOutput }

fn state(value: fsrs::ItemState) -> StateOutput {
    StateOutput { stability: value.memory.stability, difficulty: value.memory.difficulty, interval_days: value.interval }
}

fn calculate(input: ReviewInput) -> Result<ReviewOutput, String> {
    if !input.desired_retention.is_finite() || !(0.01..0.999).contains(&input.desired_retention) {
        return Err("desired_retention must be finite and in 0.01..0.999".into());
    }
    let previous = match (input.stability, input.difficulty) {
        (Some(stability), Some(difficulty))
            if stability.is_finite() && stability > 0.0
                && difficulty.is_finite() && (1.0..=10.0).contains(&difficulty) =>
            Some(MemoryState { stability, difficulty }),
        (None, None) => None,
        _ => return Err("memory state must be null or contain valid stability and difficulty".into()),
    };
    let states = FSRS::default()
        .next_states(previous, input.desired_retention, input.elapsed_days)
        .map_err(|error| error.to_string())?;
    Ok(ReviewOutput {
        again: state(states.again), hard: state(states.hard),
        good: state(states.good), easy: state(states.easy),
    })
}

fn calculate_json(input_json: &str) -> String {
    let result = std::panic::catch_unwind(|| {
        let input: ReviewInput = serde_json::from_str(input_json).map_err(|error| error.to_string())?;
        calculate(input)
    });
    match result {
        Ok(Ok(output)) => serde_json::to_string(&output)
            .unwrap_or_else(|error| serde_json::json!({ "error": error.to_string() }).to_string()),
        Ok(Err(message)) => serde_json::json!({ "error": message }).to_string(),
        Err(_) => serde_json::json!({ "error": "native FSRS bridge panicked" }).to_string(),
    }
}

/// Instance native method on the Kotlin object; JNI receives JObject ("this").
#[no_mangle]
pub extern "system" fn Java_com_example_japanesewritingtrainer_UpstreamFsrsNative_nativeReview(
    mut env: JNIEnv, _this: JObject, input_json: JString,
) -> jstring {
    let input: String = match env.get_string(&input_json) {
        Ok(value) => value.into(),
        Err(error) => {
            let message = serde_json::json!({ "error": error.to_string() }).to_string();
            return env.new_string(message).map(|value| value.into_raw())
                .unwrap_or(std::ptr::null_mut());
        }
    };
    let output = calculate_json(&input);
    env.new_string(output).map(|value| value.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn input(stability: Option<f32>, difficulty: Option<f32>, elapsed_days: u32) -> ReviewInput {
        ReviewInput { stability, difficulty, elapsed_days, desired_retention: 0.9 }
    }

    #[test]
    fn bridge_output_matches_upstream_for_new_card_and_all_ratings() {
        let bridged = calculate(input(None, None, 0)).unwrap();
        let upstream = FSRS::default().next_states(None, 0.9, 0).unwrap();
        assert_eq!(bridged.again, state(upstream.again));
        assert_eq!(bridged.hard, state(upstream.hard));
        assert_eq!(bridged.good, state(upstream.good));
        assert_eq!(bridged.easy, state(upstream.easy));
    }

    #[test]
    fn bridge_output_matches_upstream_for_existing_card() {
        let previous = MemoryState { stability: 7.0, difficulty: 5.0 };
        let bridged = calculate(input(Some(7.0), Some(5.0), 7)).unwrap();
        let upstream = FSRS::default().next_states(Some(previous), 0.9, 7).unwrap();
        assert_eq!(bridged.again, state(upstream.again));
        assert_eq!(bridged.hard, state(upstream.hard));
        assert_eq!(bridged.good, state(upstream.good));
        assert_eq!(bridged.easy, state(upstream.easy));
    }

    #[test]
    fn rejects_incomplete_or_invalid_memory_state() {
        assert!(calculate(input(Some(2.0), None, 1)).is_err());
        assert!(calculate(input(Some(f32::NAN), Some(5.0), 1)).is_err());
        assert!(calculate(input(Some(2.0), Some(11.0), 1)).is_err());
    }

    #[test]
    fn rejects_invalid_retention() {
        let mut request = input(None, None, 0);
        request.desired_retention = f32::NAN;
        assert!(calculate(request).is_err());
    }

    #[test]
    fn json_boundary_returns_structured_error() {
        let response: serde_json::Value = serde_json::from_str(&calculate_json("{not-json")).unwrap();
        assert!(response["error"].as_str().is_some());
    }
}
