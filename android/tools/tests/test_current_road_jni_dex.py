"""Minimal DEX regressions for definition-only verification of JNI-created metadata."""
import importlib.util
from pathlib import Path
import struct
import unittest

SPEC = importlib.util.spec_from_file_location("routing_jni", Path(__file__).resolve().parents[1] / "verify_routing_jni_contract.py")
JNI = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(JNI)
ROAD = "Lapp/organicmaps/sdk/routing/RoadSpeedLimitInfo;"


def dex(define=True, double_argument=True):
    strings = [ROAD, "D", "J", "V", "<init>", "VDJJ" if double_argument else "VJJJ"]
    data = bytearray(0x70)
    data[:8] = b"dex\n035\0"
    string_offset = len(data)
    data.extend(bytes(4 * len(strings)))
    type_offset = len(data)
    data.extend(struct.pack("<IIII", 0, 1, 2, 3))
    proto_offset = len(data)
    data.extend(bytes(12))
    method_offset = len(data)
    data.extend(struct.pack("<HHI", 0, 0, 4))
    class_offset = len(data)
    data.extend(bytes(32))
    params_offset = len(data)
    data.extend(struct.pack("<IHHH", 3, 1 if double_argument else 2, 2, 2))
    class_data_offset = len(data)
    # Zero fields; one direct method definition: diff=0, public constructor flags=1, code offset=0.
    data.extend(bytes([0, 0, 1, 0, 0, 1, 0]))
    for i, text in enumerate(strings):
        struct.pack_into("<I", data, string_offset + 4 * i, len(data))
        data.extend(bytes([len(text)]) + text.encode() + b"\0")
    struct.pack_into("<II", data, 0x38, len(strings), string_offset)
    struct.pack_into("<II", data, 0x40, 4, type_offset)
    struct.pack_into("<II", data, 0x48, 1, proto_offset)
    struct.pack_into("<II", data, 0x58, 1, method_offset)
    struct.pack_into("<II", data, 0x60, 1, class_offset)
    struct.pack_into("<III", data, proto_offset, 5, 3, params_offset)
    struct.pack_into("<IIIIIIII", data, class_offset, 0, 1, 0, 0, 0, 0, class_data_offset if define else 0, 0)
    return bytes(data)


class CurrentRoadJniDexTest(unittest.TestCase):
    def test_java_constructor_contract_accepts_renamed_parameters(self):
        self.assertTrue(JNI.has_current_road_constructor(
            "public RoadSpeedLimitInfo( double posted, final long observed, long identity ) {}"))
        self.assertFalse(JNI.has_current_road_constructor(
            "public RoadSpeedLimitInfo(long posted, long observed, long identity) {}"))
        self.assertFalse(JNI.has_current_road_constructor(
            "public RoadSpeedLimitInfo(double posted, double observed, long identity) {}"))


    def test_defined_constructor_has_exact_double_long_long_signature(self):
        self.assertIn((ROAD, "<init>", "(DJJ)V"), JNI.dex_defined_methods(dex()))

    def test_reference_without_definition_cannot_satisfy_shrinking_check(self):
        self.assertNotIn((ROAD, "<init>", "(DJJ)V"), JNI.dex_defined_methods(dex(define=False)))

    def test_changed_parameter_type_does_not_satisfy_jni_signature(self):
        self.assertNotIn((ROAD, "<init>", "(DJJ)V"), JNI.dex_defined_methods(dex(double_argument=False)))


if __name__ == "__main__":
    unittest.main()
