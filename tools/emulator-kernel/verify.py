#!/usr/bin/env python3
"""Fail-closed static gate for the isolated API 34 x86_64 test target.

This is a bounded ELF reader, not a loader or a claim of runtime compatibility.
It deliberately rejects the generic DDK artifact which caused the ABI collision.
"""
import argparse
import hashlib
import json
import re
import struct
from pathlib import Path

KERNEL_COMMIT = "7e35917775b8b3e3346a87f294e334e258bf15e6"
KERNEL_RELEASE = "6.1.23-android14-4-00257-g7e35917775b8-ab9964412"
VERMAGIC = KERNEL_RELEASE + " SMP preempt mod_unload modversions "
CONFIG_SHA256 = "45b89fa54f7bee763e356a77a41f17905b3f958e6441504dce0f3016f90aff80"
MODULE_LAYOUT_CRC = 0x38786C34


def require(condition, message):
    if not condition:
        raise ValueError(message)


class Elf:
    def __init__(self, data):
        self.data = data
        require(64 <= len(data) <= 32 * 1024 * 1024, "ELF size outside bounds")
        require(data[:7] == b"\x7fELF\x02\x01\x01", "Expected little-endian ELF64")
        require(self.u16(16) == 1 and self.u16(18) == 62, "Expected x86_64 ET_REL")
        table, count, names = self.u64(40), self.u16(60), self.u16(62)
        require(self.u16(58) == 64 and 0 < count <= 4096, "Invalid section table")
        require(names < count and table + count * 64 <= len(data), "Truncated section table")
        self.sections = []
        for i in range(count):
            p = table + 64 * i
            self.sections.append(dict(name_offset=self.u32(p), type=self.u32(p + 4),
                                      offset=self.u64(p + 24), size=self.u64(p + 32),
                                      link=self.u32(p + 40), info=self.u32(p + 44),
                                      entsize=self.u64(p + 56)))
        strings = self.payload(self.sections[names])
        for section in self.sections:
            section["name"] = self.string(strings, section["name_offset"])

    def u16(self, p):
        return struct.unpack_from("<H", self.data, p)[0]

    def u32(self, p):
        return struct.unpack_from("<I", self.data, p)[0]

    def u64(self, p):
        return struct.unpack_from("<Q", self.data, p)[0]

    @staticmethod
    def string(data, offset):
        require(0 <= offset < len(data), "String offset outside table")
        end = data.find(b"\0", offset)
        require(end >= offset, "Unterminated ELF string")
        return data[offset:end].decode("ascii", errors="strict")

    def payload(self, section):
        end = section["offset"] + section["size"]
        require(end <= len(self.data), "Section outside file")
        return self.data[section["offset"]:end]

    def section(self, name):
        matches = [s for s in self.sections if s["name"] == name]
        require(len(matches) == 1, f"Missing or duplicate section {name}")
        return matches[0]

    def symbol_name(self, symtab, index):
        require(symtab["type"] == 2 and symtab["entsize"] == 24, "Invalid symbol table")
        require(index * 24 + 24 <= symtab["size"], "Symbol outside table")
        require(symtab["link"] < len(self.sections), "Invalid string-table link")
        return self.string(self.payload(self.sections[symtab["link"]]),
                           self.u32(symtab["offset"] + index * 24))


def inspect_module(data, check_manager=None):
    elf = Elf(data)
    module = elf.section(".gnu.linkonce.this_module")
    require(module["size"] == 0x380, "struct module size differs from target 0x380")
    reloc = elf.section(".rela.gnu.linkonce.this_module")
    require(reloc["type"] == 4 and reloc["entsize"] == 24 and reloc["size"] % 24 == 0,
            "Invalid module RELA table")
    require(reloc["link"] < len(elf.sections), "Invalid symbol link")
    require(reloc["info"] < len(elf.sections)
            and elf.sections[reloc["info"]] is module, "Wrong module relocation target")
    symtab = elf.sections[reloc["link"]]
    functions = {}
    seen = set()
    for p in range(reloc["offset"], reloc["offset"] + reloc["size"], 24):
        offset, info = elf.u64(p), elf.u64(p + 8)
        require(info & 0xFFFFFFFF == 1, "Unexpected module pointer relocation type")
        require(offset not in seen and offset + 8 <= module["size"], "Invalid relocation range")
        seen.add(offset)
        require(elf.u64(module["offset"] + offset) == 0, "Nonzero module relocation target")
        name = elf.symbol_name(symtab, info >> 32)
        require(name not in functions, "Duplicate module function relocation")
        functions[name] = offset
    require(functions.get("init_module") == 0x150, "init pointer does not match target 0x150")
    require(functions.get("cleanup_module") == 0x370, "exit pointer does not match target 0x370")
    modinfo = elf.payload(elf.section(".modinfo")).split(b"\0")
    require(b"vermagic=" + VERMAGIC.encode() in modinfo, "Target vermagic mismatch")
    versions = elf.payload(elf.section("__versions"))
    require(len(versions) > 0 and len(versions) % 64 == 0, "Missing version CRC records")
    crc_records = []
    for p in range(0, len(versions), 64):
        name = Elf.string(versions[p:p + 64], 8)
        if name == "module_layout":
            crc_records.append(struct.unpack_from("<Q", versions, p)[0])
    require(crc_records == [MODULE_LAYOUT_CRC], "module_layout CRC differs from target")
    if check_manager:
        for token in (check_manager["KSU_PACKAGE_NAME"], check_manager["KSU_EXPECTED_HASH"]):
            require(token.encode("ascii") in data, "Missing JWSK manager identity pin")
    return dict(sha256=hashlib.sha256(data).hexdigest(), bytes=len(data),
                module_size=module["size"], init_offset=0x150, exit_offset=0x370,
                module_layout_crc=f"0x{MODULE_LAYOUT_CRC:08x}", vermagic=VERMAGIC,
                runtime_verified=False)


def config_values(path):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("CONFIG_") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
        elif re.fullmatch(r"# CONFIG_\w+ is not set", line):
            values[line[2:-11]] = "n"
    return values


def check_config(original, generated):
    require(hashlib.sha256(original.read_bytes()).hexdigest() == CONFIG_SHA256,
            "Captured device configuration hash mismatch")
    before, after = config_values(original), config_values(generated)
    # Host pahole revision can differ without altering ABI; compiler flags cannot.
    allowed = {"CONFIG_PAHOLE_VERSION"}
    changes = {key: [before.get(key), after.get(key)] for key in before.keys() | after.keys()
               if before.get(key) != after.get(key) and key not in allowed}
    require(not changes, "Configuration drift: " + json.dumps(changes, sort_keys=True))


def check_release_header(path):
    """Check the actual compile-time release, not make's SCM display target."""
    text = path.read_text(encoding="ascii")
    definitions = re.findall(r'^\s*#\s*define\s+UTS_RELEASE\s+"([^"\r\n]*)"\s*$',
                             text, re.MULTILINE)
    require(definitions == [KERNEL_RELEASE], "Generated UTS_RELEASE does not match target")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    module = commands.add_parser("module")
    module.add_argument("path", type=Path)
    module.add_argument("--identity", type=Path)
    config = commands.add_parser("config")
    config.add_argument("original", type=Path)
    config.add_argument("generated", type=Path)
    release = commands.add_parser("release-header")
    release.add_argument("path", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "config":
            check_config(args.original, args.generated)
            print("Captured target configuration preserved")
        elif args.command == "release-header":
            check_release_header(args.path)
            print("Actual compiled UTS_RELEASE matches captured target")
        else:
            identity = None
            if args.identity:
                identity = dict(line.split("=", 1) for line in
                                args.identity.read_text().splitlines()
                                if line and not line.startswith("#") and "=" in line)
            print(json.dumps(inspect_module(args.path.read_bytes(), identity), indent=2))
    except (ValueError, IndexError, KeyError, UnicodeError, struct.error, OSError) as exc:
        parser.exit(1, f"Target compatibility check failed: {exc}\n")


if __name__ == "__main__":
    main()
