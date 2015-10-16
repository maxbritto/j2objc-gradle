/*
 * Copyright (c) 2015 the authors of j2objc-gradle (see AUTHORS file)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.j2objccontrib.j2objcgradle.tasks

import com.github.j2objccontrib.j2objcgradle.J2objcConfig
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException;

/**
 * Utils tests.
 */
// Double quotes are used throughout this file to avoid escaping single quotes
// which are common in Podfiles, used extensively within these tests
class PodspecTaskTest {

    // TODO: use this within future tests
    private Project proj

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    void testPodspecWrite() {
        String j2objcHome
        J2objcConfig j2objcConfig
        (proj, j2objcHome, j2objcConfig) =
                TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                        applyJavaPlugin: true,
                        createJ2objcConfig: true))

        // Hacky way to get this test working on Windows
        if (Utils.isWindows()) {
            j2objcHome = j2objcHome.replace('/', '\\')
        }

        // Needed for podspecDebug
        proj.file(proj.buildDir).mkdir()

        PodspecTask j2objcPodspec = (PodspecTask) proj.tasks.create(name: 'j2objcPodspec', type: PodspecTask)

        String podNameDebug = "j2objc-${proj.getName()}-debug"
        String podNameRelease = "j2objc-${proj.getName()}-release"
        String libName = "${proj.getName()}-j2objc"
        assert podNameDebug == j2objcPodspec.getPodNameDebug()
        assert podNameRelease == j2objcPodspec.getPodNameRelease()

        // @TaskAction method that doed the real work
        j2objcPodspec.podspecWrite()

        List<String> expectedPodspecDebug = [
                "Pod::Spec.new do |spec|",
                "  spec.name = '$podNameDebug'",
                "  spec.version = '1.0'",
                "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'",
                "  spec.resources = 'j2objcOutputs/src/main/resources/**/*'",
                "  spec.requires_arc = true",
                "  spec.libraries = 'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', '$libName'",
                "  spec.xcconfig = {",
                "    'HEADER_SEARCH_PATHS' => '${j2objcHome}/include ${proj.file('build/j2objcOutputs/src/main/objc')}'",
                "  }",
                "  spec.ios.xcconfig = {",
                "    'LIBRARY_SEARCH_PATHS' => '${j2objcHome}/lib ${proj.file('build/j2objcOutputs/lib/iosDebug').absolutePath}'",
                "  }",
                "  spec.osx.xcconfig = {",
                "    'LIBRARY_SEARCH_PATHS' => '${j2objcHome}/lib/macosx ${proj.file('build/j2objcOutputs/lib/x86_64Debug').absolutePath}'",
                "  }",
                "  spec.watchos.xcconfig = {",
                "    'LIBRARY_SEARCH_PATHS' => '${j2objcHome}/lib ${proj.file('build/j2objcOutputs/lib/iosDebug').absolutePath}'",
                "  }",
                "  spec.ios.deployment_target = '8.3'",
                "  spec.osx.deployment_target = '10.8'",
                "  spec.watchos.deployment_target = '2.0'",
                "  spec.osx.frameworks = 'ExceptionHandling'",
                "end"]
        File podspecDebug = proj.file("build/${podNameDebug}.podspec")
        List<String> readPodspecDebug = podspecDebug.readLines()
        assert expectedPodspecDebug == readPodspecDebug

        // Release Podspec
        List<String> expectedPodspecRelease = [
                "Pod::Spec.new do |spec|",
                "  spec.name = '$podNameRelease'",
                "  spec.version = '1.0'",
                "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'",
                "  spec.resources = 'j2objcOutputs/src/main/resources/**/*'",
                "  spec.requires_arc = true",
                "  spec.libraries = 'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', '$libName'",
                "  spec.xcconfig = {",
                "    'HEADER_SEARCH_PATHS' => '${j2objcHome}/include ${proj.file('build/j2objcOutputs/src/main/objc')}'",
                "  }",
                "  spec.ios.xcconfig = {",
                "    'LIBRARY_SEARCH_PATHS' => '${j2objcHome}/lib ${proj.file('build/j2objcOutputs/lib/iosRelease').absolutePath}'",
                "  }",
                "  spec.osx.xcconfig = {",
                "    'LIBRARY_SEARCH_PATHS' => '${j2objcHome}/lib/macosx ${proj.file('build/j2objcOutputs/lib/x86_64Release').absolutePath}'",
                "  }",
                "  spec.watchos.xcconfig = {",
                "    'LIBRARY_SEARCH_PATHS' => '${j2objcHome}/lib ${proj.file('build/j2objcOutputs/lib/iosRelease').absolutePath}'",
                "  }",
                "  spec.ios.deployment_target = '8.3'",
                "  spec.osx.deployment_target = '10.8'",
                "  spec.watchos.deployment_target = '2.0'",
                "  spec.osx.frameworks = 'ExceptionHandling'",
                "end"]
        File podspecRelease = proj.file("build/${podNameRelease}.podspec")
        List<String> readPodspecRelease = podspecRelease.readLines()
        assert expectedPodspecRelease == readPodspecRelease
    }

    @Test
    void testGenPodspec() {
        List<String> podspecDebug = PodspecTask.genPodspec(
                'POD-NAME', '/HEADER_INCLUDE', 'MAIN-RESOURCES',
                'LIB-NAME', '/J2OBJC_HOME',
                '/LIB-DIR-IOS', '/LIB-DIR-OSX', '/LIB-DIR-WATCHOS',
                // Using non-existent OS version numbers to ensure that no defaults are being used
                '8.3.1', '10.8.1', '2.0.1').split('\n')

        List<String> expectedPodspecDebug = [
                "Pod::Spec.new do |spec|",
                "  spec.name = 'POD-NAME'",
                "  spec.version = '1.0'",
                "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'",
                "  spec.resources = 'MAIN-RESOURCES/**/*'",
                "  spec.requires_arc = true",
                "  spec.libraries = 'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', 'LIB-NAME'",
                "  spec.xcconfig = {",
                "    'HEADER_SEARCH_PATHS' => '/J2OBJC_HOME/include /HEADER_INCLUDE'",
                "  }",
                "  spec.ios.xcconfig = {",
                "    'LIBRARY_SEARCH_PATHS' => '/J2OBJC_HOME/lib /LIB-DIR-IOS'",
                "  }",
                "  spec.osx.xcconfig = {",
                "    'LIBRARY_SEARCH_PATHS' => '/J2OBJC_HOME/lib/macosx /LIB-DIR-OSX'",
                "  }",
                "  spec.watchos.xcconfig = {",
                "    'LIBRARY_SEARCH_PATHS' => '/J2OBJC_HOME/lib /LIB-DIR-WATCHOS'",
                "  }",
                "  spec.ios.deployment_target = '8.3.1'",
                "  spec.osx.deployment_target = '10.8.1'",
                "  spec.watchos.deployment_target = '2.0.1'",
                "  spec.osx.frameworks = 'ExceptionHandling'",
                "end"]

        assert expectedPodspecDebug == podspecDebug
    }

    @Test
    void testValidatePodspecPath_Ok() {
        PodspecTask.validatePodspecPath('/dir/dir', false)
        PodspecTask.validatePodspecPath('dir/dir', true)
    }

    @Test(expected=InvalidUserDataException.class)
    void testValidatePodspecPath_DoubleSlash() {
        PodspecTask.validatePodspecPath('/dir//dir', false)
    }

    @Test(expected=InvalidUserDataException.class)
    void testValidatePodspecPath_TrailingSlash() {
        PodspecTask.validatePodspecPath('/dir/dir/', false)
    }

    @Test(expected=InvalidUserDataException.class)
    void testValidatePodspecPath_AbsoluteInvalid() {
        PodspecTask.validatePodspecPath('/dir/dir', true)
    }

    @Test(expected=InvalidUserDataException.class)
    void testValidatePodspecPath_RelativeInvalid() {
        PodspecTask.validatePodspecPath('dir/dir', false)
    }
}
