use core::ffi::c_void;

#[unsafe(no_mangle)]
pub extern "system" fn Java_moe_shizuku_manager_root_JwskNativeCore_nativeApiVersion(
    _env: *mut c_void,
    _object: *mut c_void,
) -> i32 {
    120
}

fn mix64(input: u64) -> u64 {
    let mut value = input;
    value = (value ^ (value >> 30)).wrapping_mul(0xbf58_476d_1ce4_e5b9);
    value = (value ^ (value >> 27)).wrapping_mul(0x94d0_49bb_1331_11eb);
    value ^ (value >> 31)
}

/// SplitMix64 finalizer used to add a compact integrity stamp to local JWSK
/// journal records. The JNI arguments are primitives only, which keeps this
/// Android library dependency-free and avoids handing Java-owned memory to
/// native code.
#[unsafe(no_mangle)]
pub extern "system" fn Java_moe_shizuku_manager_root_JwskNativeCore_nativeMix64(
    _env: *mut c_void,
    _object: *mut c_void,
    input: i64,
) -> i64 {
    mix64(input as u64) as i64
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn known_vectors_match_the_kotlin_fallback() {
        assert_eq!(mix64(0), 0);
        assert_eq!(mix64(1), 0x5692_161d_100b_05e5);
        assert_eq!(mix64(u64::MAX), 0xb4d0_55fc_f2cb_bd7b);
    }

    #[test]
    fn jni_preserves_signed_bits() {
        let actual = Java_moe_shizuku_manager_root_JwskNativeCore_nativeMix64(
            core::ptr::null_mut(),
            core::ptr::null_mut(),
            i64::MIN,
        );
        assert_eq!(actual as u64, mix64(1_u64 << 63));
    }

    #[test]
    fn jni_abi_version_is_current() {
        assert_eq!(
            Java_moe_shizuku_manager_root_JwskNativeCore_nativeApiVersion(
                core::ptr::null_mut(),
                core::ptr::null_mut(),
            ),
            120
        );
    }
}
