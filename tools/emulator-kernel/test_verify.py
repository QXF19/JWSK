import struct
import tempfile
import unittest
from pathlib import Path

import verify


def fixture(size=0x380, init=0x150, exit_offset=0x370, crc=verify.MODULE_LAYOUT_CRC,
            vermagic=verify.VERMAGIC, dirty=False):
    names = ["", ".shstrtab", ".gnu.linkonce.this_module", ".modinfo", "__versions",
             ".symtab", ".strtab", ".rela.gnu.linkonce.this_module"]
    strings, offsets = bytearray(b"\0"), {"": 0}
    for name in names[1:]:
        offsets[name] = len(strings)
        strings.extend(name.encode() + b"\0")
    module = bytearray(size)
    if dirty:
        struct.pack_into("<Q", module, init, 0x1234)
    symbols = bytes(24) + struct.pack("<IBBHQQ", 1, 0x12, 0, 2, 5, 1)
    symbols += struct.pack("<IBBHQQ", 13, 0x12, 0, 2, 5, 1)
    versions = struct.pack("<Q", crc) + b"module_layout\0".ljust(56, b"\0")
    relocations = struct.pack("<QQq", init, (1 << 32) | 1, 0)
    relocations += struct.pack("<QQq", exit_offset, (2 << 32) | 1, 0)
    contents = [b"", bytes(strings), module, b"vermagic=" + vermagic.encode() + b"\0",
                versions, symbols, b"\0init_module\0cleanup_module\0", relocations]
    data, sections = bytearray(64), []
    for i, content in enumerate(contents):
        data.extend(bytes((-len(data)) % 8))
        offset = len(data)
        data.extend(content)
        kind = {0: 0, 1: 3, 5: 2, 6: 3, 7: 4}.get(i, 1)
        link, info, entry = (6, 1, 24) if i == 5 else ((5, 2, 24) if i == 7 else (0, 0, 0))
        sections.append(struct.pack("<IIQQQQIIQQ", offsets[names[i]], kind, 0, 0,
                                    offset, len(content), link, info, 8, entry))
    data.extend(bytes((-len(data)) % 8))
    table = len(data)
    data.extend(b"".join(sections))
    struct.pack_into("<16sHHIQQQIHHHHHH", data, 0,
                     b"\x7fELF\x02\x01\x01".ljust(16, b"\0"),
                     1, 62, 1, 0, 0, table, 0, 64, 0, 0, 64, len(sections), 1)
    return bytes(data)


class ModuleGateTests(unittest.TestCase):
    def test_valid_target_has_no_runtime_success_claim(self):
        result = verify.inspect_module(fixture())
        self.assertEqual(result["module_layout_crc"], "0x38786c34")
        self.assertIs(result["runtime_verified"], False)

    def test_generic_ddk_layout_rejected(self):
        with self.assertRaisesRegex(ValueError, "size"):
            verify.inspect_module(fixture(size=0x400, init=0x180, exit_offset=0x3b0))

    def test_wrong_pointer_offsets_rejected(self):
        with self.assertRaisesRegex(ValueError, "init pointer"):
            verify.inspect_module(fixture(init=0x180))

    def test_wrong_crc_rejected(self):
        with self.assertRaisesRegex(ValueError, "CRC"):
            verify.inspect_module(fixture(crc=0))

    def test_wrong_version_rejected(self):
        with self.assertRaisesRegex(ValueError, "vermagic"):
            verify.inspect_module(fixture(vermagic="6.1.166 SMP"))

    def test_nonzero_slot_rejected(self):
        with self.assertRaisesRegex(ValueError, "Nonzero"):
            verify.inspect_module(fixture(dirty=True))

    def test_wrong_architecture_rejected(self):
        data = bytearray(fixture())
        struct.pack_into("<H", data, 18, 183)
        with self.assertRaisesRegex(ValueError, "x86_64"):
            verify.inspect_module(bytes(data))

    def test_truncated_section_table_rejected(self):
        with self.assertRaisesRegex(ValueError, "section table"):
            verify.inspect_module(fixture()[:-1])

    def test_identity_gate_rejects_other_manager(self):
        with self.assertRaisesRegex(ValueError, "identity pin"):
            verify.inspect_module(fixture(), {"KSU_PACKAGE_NAME": "cn.jiangwang.jwsk",
                                              "KSU_EXPECTED_HASH": "0" * 64})

    def test_exact_config_snapshot(self):
        path = Path(__file__).with_name("emulator-api34.config")
        verify.check_config(path, path)

    def test_configuration_cannot_disable_safety(self):
        source = Path(__file__).with_name("emulator-api34.config")
        with tempfile.TemporaryDirectory() as directory:
            changed = Path(directory) / "changed.config"
            changed.write_text(source.read_text().replace("CONFIG_MODVERSIONS=y",
                                                         "# CONFIG_MODVERSIONS is not set"))
            with self.assertRaisesRegex(ValueError, "Configuration drift"):
                verify.check_config(source, changed)

    def test_compile_time_release_header(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "utsrelease.h"
            path.write_text('#define UTS_RELEASE "' + verify.KERNEL_RELEASE + '"\n')
            verify.check_release_header(path)

    def test_wrong_compile_time_release_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "utsrelease.h"
            for text in ('#define UTS_RELEASE "6.1.23"\n', '',
                         ('#define UTS_RELEASE "' + verify.KERNEL_RELEASE + '"\n') * 2):
                path.write_text(text)
                with self.assertRaisesRegex(ValueError, "UTS_RELEASE"):
                    verify.check_release_header(path)


if __name__ == "__main__":
    unittest.main()
