use core::ffi::c_void;

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
    let mut value = input as u64;
    value = (value ^ (value >> 30)).wrapping_mul(0xbf58_476d_1ce4_e5b9);
    value = (value ^ (value >> 27)).wrapping_mul(0x94d0_49bb_1331_11eb);
    (value ^ (value >> 31)) as i64
}
