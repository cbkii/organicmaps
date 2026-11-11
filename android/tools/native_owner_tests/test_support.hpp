#pragma once
// Headless assertion/registration boundary only. Production owner .cpp/.hpp files are unmodified.
#include "testing/testregister.hpp"
#include <stdexcept>
#define UNIT_TEST(name) void UnitTest_##name(); static TestRegister reg_##name(#name,__FILE__,&UnitTest_##name); void UnitTest_##name()
#define TEST(x,...) do { if (!(x)) throw std::runtime_error(#x); } while(false)
#define TEST_EQUAL(x,y,...) TEST((x)==(y))
#define TEST_NOT_EQUAL(x,y,...) TEST((x)!=(y))
#define TEST_LESS(x,y,...) TEST((x)<(y))
#define TEST_LESS_OR_EQUAL(x,y,...) TEST((x)<=(y))
#define TEST_GREATER(x,y,...) TEST((x)>(y))
#define TEST_GREATER_OR_EQUAL(x,y,...) TEST((x)>=(y))
