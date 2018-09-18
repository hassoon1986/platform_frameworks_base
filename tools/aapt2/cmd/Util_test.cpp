/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "Util.h"

#include "AppInfo.h"
#include "split/TableSplitter.h"
#include "test/Builders.h"
#include "test/Test.h"

namespace aapt {
#define EXPECT_CONFIG_EQ(constraints, config) \
    EXPECT_EQ(constraints.configs.size(), 1); \
    EXPECT_EQ(*constraints.configs.begin(), config); \
    constraints.configs.clear();

TEST(UtilTest, SplitNamesAreSanitized) {
    AppInfo app_info{"com.pkg"};
    SplitConstraints split_constraints{
        {test::ParseConfigOrDie("en-rUS-land"), test::ParseConfigOrDie("b+sr+Latn")}};

    const auto doc = GenerateSplitManifest(app_info, split_constraints);
    const auto &root = doc->root;
    EXPECT_EQ(root->name, "manifest");
    // split names cannot contain hyphens or plus signs.
    EXPECT_EQ(root->FindAttribute("", "split")->value, "config.b_sr_Latn_en_rUS_land");
    // but we should use resource qualifiers verbatim in 'targetConfig'.
    EXPECT_EQ(root->FindAttribute("", "targetConfig")->value, "b+sr+Latn,en-rUS-land");
}

TEST (UtilTest, LongVersionCodeDefined) {
  auto doc = test::BuildXmlDom(R"(
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="com.android.aapt.test" android:versionCode="0x1" android:versionCodeMajor="0x1">
      </manifest>)");
  SetLongVersionCode(doc->root.get(), 42);

  auto version_code = doc->root->FindAttribute(xml::kSchemaAndroid, "versionCode");
  ASSERT_NE(version_code, nullptr);
  EXPECT_EQ(version_code->value, "0x0000002a");

  ASSERT_NE(version_code->compiled_value, nullptr);
  auto compiled_version_code = ValueCast<BinaryPrimitive>(version_code->compiled_value.get());
  ASSERT_NE(compiled_version_code, nullptr);
  EXPECT_EQ(compiled_version_code->value.data, 42U);

  auto version_code_major = doc->root->FindAttribute(xml::kSchemaAndroid, "versionCodeMajor");
  EXPECT_EQ(version_code_major, nullptr);
}

TEST (UtilTest, LongVersionCodeUndefined) {
  auto doc = test::BuildXmlDom(R"(
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.android.aapt.test">
        </manifest>)");
  SetLongVersionCode(doc->root.get(), 420000000000);

  auto version_code = doc->root->FindAttribute(xml::kSchemaAndroid, "versionCode");
  ASSERT_NE(version_code, nullptr);
  EXPECT_EQ(version_code->value, "0xc9f36800");

  ASSERT_NE(version_code->compiled_value, nullptr);
  auto compiled_version_code = ValueCast<BinaryPrimitive>(version_code->compiled_value.get());
  ASSERT_NE(compiled_version_code, nullptr);
  EXPECT_EQ(compiled_version_code->value.data, 0xc9f36800);

  auto version_code_major = doc->root->FindAttribute(xml::kSchemaAndroid, "versionCodeMajor");
  ASSERT_NE(version_code_major, nullptr);
  EXPECT_EQ(version_code_major->value, "0x00000061");

  ASSERT_NE(version_code_major->compiled_value, nullptr);
  auto compiled_version_code_major = ValueCast<BinaryPrimitive>(
      version_code_major->compiled_value.get());
  ASSERT_NE(compiled_version_code_major, nullptr);
  EXPECT_EQ(compiled_version_code_major->value.data, 0x61);
}


TEST (UtilTest, ParseSplitParameter) {
  IDiagnostics* diagnostics = test::ContextBuilder().Build().get()->GetDiagnostics();
  std::string path;
  SplitConstraints constraints;
  ConfigDescription expected_configuration;

  // ========== Test IMSI ==========
  // mcc: 'mcc[0-9]{3}'
  // mnc: 'mnc[0-9]{1,3}'
  ASSERT_TRUE(ParseSplitParameter(":mcc310",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setMcc(0x0136)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":mcc310-mnc004",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setMcc(0x0136)
      .setMnc(0x0004)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":mcc310-mnc000",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setMcc(0x0136)
      .setMnc(0xFFFF)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  // ========== Test LOCALE ==========
  // locale: '[a-z]{2,3}(-r[a-z]{2})?'
  // locale: 'b+[a-z]{2,3}(+[a-z[0-9]]{2})?'
  ASSERT_TRUE(ParseSplitParameter(":es",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setLanguage(0x6573)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":fr-rCA",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setLanguage(0x6672)
      .setCountry(0x4341)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":b+es+419",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setLanguage(0x6573)
      .setCountry(0xA424)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  // ========== Test SCREEN_TYPE ==========
  // orientation: '(port|land|square)'
  // touchscreen: '(notouch|stylus|finger)'
  // density" '(anydpi|nodpi|ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|[0-9]*dpi)'
  ASSERT_TRUE(ParseSplitParameter(":square",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setOrientation(0x03)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":stylus",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setTouchscreen(0x02)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":xxxhdpi",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setDensity(0x0280)
      .setSdkVersion(0x0004) // version [any density requires donut]
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":land-xhdpi-finger",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setOrientation(0x02)
      .setTouchscreen(0x03)
      .setDensity(0x0140)
      .setSdkVersion(0x0004) // version [any density requires donut]
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  // ========== Test INPUT ==========
  // keyboard: '(nokeys|qwerty|12key)'
  // navigation: '(nonav|dpad|trackball|wheel)'
  // inputFlags: '(keysexposed|keyshidden|keyssoft)'
  // inputFlags: '(navexposed|navhidden)'
  ASSERT_TRUE(ParseSplitParameter(":qwerty",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setKeyboard(0x02)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":dpad",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setNavigation(0x02)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":keyssoft-navhidden",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setInputFlags(0x0B)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":keyshidden-nokeys-navexposed-trackball",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setKeyboard(0x01)
      .setNavigation(0x03)
      .setInputFlags(0x06)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  // ========== Test SCREEN_SIZE ==========
  // screenWidth/screenHeight: '[0-9]+x[0-9]+'
  ASSERT_TRUE(ParseSplitParameter(":1920x1080",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setScreenWidth(0x0780)
      .setScreenHeight(0x0438)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  // ========== Test VERSION ==========
  // version 'v[0-9]+'

  // ========== Test SCREEN_CONFIG ==========
  // screenLayout [direction]: '(ldltr|ldrtl)'
  // screenLayout [size]: '(small|normal|large|xlarge)'
  // screenLayout [long]: '(long|notlong)'
  // uiMode [type]: '(desk|car|television|appliance|watch|vrheadset)'
  // uiMode [night]: '(night|notnight)'
  // smallestScreenWidthDp: 'sw[0-9]dp'
  ASSERT_TRUE(ParseSplitParameter(":ldrtl",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setScreenLayout(0x80)
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":small",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setScreenLayout(0x01)
      .setSdkVersion(0x0004) // screenLayout (size) requires donut
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":notlong",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setScreenLayout(0x10)
      .setSdkVersion(0x0004) // screenLayout (long) requires donut
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":ldltr-normal-long",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setScreenLayout(0x62)
      .setSdkVersion(0x0004) // screenLayout (size|long) requires donut
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":car",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setUiMode(0x03)
      .setSdkVersion(0x0008) // uiMode requires froyo
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":vrheadset",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setUiMode(0x07)
      .setSdkVersion(0x001A) // uiMode 'vrheadset' requires oreo
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":television-night",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setUiMode(0x24)
      .setSdkVersion(0x0008) // uiMode requires froyo
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":sw1920dp",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setSmallestScreenWidthDp(0x0780)
      .setSdkVersion(0x000D) // smallestScreenWidthDp requires honeycomb mr2
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  // ========== Test SCREEN_SIZE_DP ==========
  // screenWidthDp: 'w[0-9]dp'
  // screenHeightDp: 'h[0-9]dp'
  ASSERT_TRUE(ParseSplitParameter(":w1920dp",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setScreenWidthDp(0x0780)
      .setSdkVersion(0x000D) // screenWidthDp requires honeycomb mr2
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":h1080dp",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setScreenHeightDp(0x0438)
      .setSdkVersion(0x000D) // screenHeightDp requires honeycomb mr2
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  // ========== Test SCREEN_CONFIG_2 ==========
  // screenLayout2: '(round|notround)'
  // colorMode: '(widecg|nowidecg)'
  // colorMode: '(highhdr|lowdr)'
  ASSERT_TRUE(ParseSplitParameter(":round",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setScreenLayout2(0x02)
      .setSdkVersion(0x0017) // screenLayout2 (round) requires marshmallow
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);

  ASSERT_TRUE(ParseSplitParameter(":widecg-highdr",
                                  diagnostics, &path, &constraints));
  expected_configuration = test::ConfigDescriptionBuilder()
      .setColorMode(0x0A)
      .setSdkVersion(0x001A) // colorMode (hdr|colour gamut) requires oreo
      .Build();
  EXPECT_CONFIG_EQ(constraints, expected_configuration);
}

TEST (UtilTest, AdjustSplitConstraintsForMinSdk) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().Build();

  IDiagnostics* diagnostics = context.get()->GetDiagnostics();
  std::vector<SplitConstraints> test_constraints;
  std::string path;

  test_constraints.push_back({});
  ASSERT_TRUE(ParseSplitParameter(":v7",
                                  diagnostics, &path, &test_constraints.back()));
  test_constraints.push_back({});
  ASSERT_TRUE(ParseSplitParameter(":xhdpi",
                                  diagnostics, &path, &test_constraints.back()));
  EXPECT_EQ(test_constraints.size(), 2);
  EXPECT_EQ(test_constraints[0].name, "v7");
  EXPECT_EQ(test_constraints[0].configs.size(), 1);
  EXPECT_NE(*test_constraints[0].configs.begin(), ConfigDescription::DefaultConfig());
  EXPECT_EQ(test_constraints[1].name, "xhdpi");
  EXPECT_EQ(test_constraints[1].configs.size(), 1);
  EXPECT_NE(*test_constraints[0].configs.begin(), ConfigDescription::DefaultConfig());

  auto adjusted_contraints = AdjustSplitConstraintsForMinSdk(26, test_constraints);
  EXPECT_EQ(adjusted_contraints.size(), 2);
  EXPECT_EQ(adjusted_contraints[0].name, "v7");
  EXPECT_EQ(adjusted_contraints[0].configs.size(), 0);
  EXPECT_EQ(adjusted_contraints[1].name, "xhdpi");
  EXPECT_EQ(adjusted_contraints[1].configs.size(), 1);
  EXPECT_NE(*adjusted_contraints[1].configs.begin(), ConfigDescription::DefaultConfig());
}

}  // namespace aapt
