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
import com.google.common.annotations.VisibleForTesting
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.util.regex.Matcher

/**
 * Updates the Xcode project with j2objc generated files and resources.
 * <p/>
 * This uses the CocoaPods dependency system. For more details see
 * https://cocoapods.org/.
 * <p/>
 * It creates a podspec file and inserts it into your project's pod file.
 * If you haven't create a pod file yet you have to run `pod init` in your
 * project folder before you run this task.
 */
@CompileStatic
class PodspecTask extends DefaultTask {

    // Generated ObjC source files and main resources
    // Not @InputDirectory as the podspec doesn't depend on the directory contents, only the path
    @Input
    File getDestSrcMainObjDirFile() {
        return J2objcConfig.from(project).getDestSrcDirFile('main', 'objc')
    }
    // Not @InputDirectory as the podspec doesn't depend on the directory contents, only the path
    @Input
    File getDestSrcMainResourcesDirFile() {
        return J2objcConfig.from(project).getDestSrcDirFile('main', 'resources')
    }

    @Input
    String getJ2objcHome() { return Utils.j2objcHome(project) }

    @Input
    File getDestLibDirFile() { return J2objcConfig.from(project).getDestLibDirFile() }

    @Input
    String getPodNameDebug() { "j2objc-${project.name}-debug" }
    @Input
    String getPodNameRelease() { "j2objc-${project.name}-release" }


    // CocoaPods podspec files that are referenced by the Podfile
    @OutputFile
    File getPodspecDebug() { new File(project.buildDir, "${getPodNameDebug()}.podspec") }
    @OutputFile
    File getPodspecRelease() { new File(project.buildDir, "${getPodNameRelease()}.podspec") }


    @TaskAction
    void podspecWrite() {
        // podspec paths must be relative to podspec file, which is in buildDir
        // NOTE: toURI() adds trailing slash in production but not in unit tests
        URI buildDir = project.buildDir.toURI()

        // Absolute path for header include, relative path for resource include
        String headerIncludePath = getDestSrcMainObjDirFile().getAbsolutePath()
        String resourceIncludePath = Utils.trimTrailingForwardSlash(
                buildDir.relativize(getDestSrcMainResourcesDirFile().toURI()).toString())

        // TODO: make this an explicit @Input
        // Same for both debug and release builds
        String libName = "${project.name}-j2objc"

        // podspec creation
        // TODO: allow custom list of libraries
        // iOS packed libraries are shared with watchOS
        String libDirIosDebug = new File(getDestLibDirFile(), '/iosDebug').absolutePath
        String libDirIosRelease = new File(getDestLibDirFile(), '/iosRelease').absolutePath
        String libDirOsxDebug = new File(getDestLibDirFile(), '/x86_64Debug').absolutePath
        String libDirOsxRelease = new File(getDestLibDirFile(), '/x86_64Release').absolutePath

        J2objcConfig j2objcConfig = J2objcConfig.from(project)

        String minIos = j2objcConfig.minIosVersion
        String minOsx = j2objcConfig.minOsxVersion
        String minWatchos = j2objcConfig.minWatchosVersion
        validateNumericVersion(minIos, 'minIosVersion')
        validateNumericVersion(minOsx, 'minOsxVersion')
        validateNumericVersion(minWatchos, 'minWatchosVersion')

        String podspecContentsDebug =
                genPodspec(getPodNameDebug(), headerIncludePath, resourceIncludePath,
                        libName, getJ2objcHome(),
                        libDirIosDebug, libDirOsxDebug, libDirIosDebug,
                        minIos, minOsx, minWatchos)
        String podspecContentsRelease =
                genPodspec(getPodNameRelease(), headerIncludePath, resourceIncludePath,
                        libName, getJ2objcHome(),
                        libDirIosRelease, libDirOsxRelease, libDirIosRelease,
                        minIos, minOsx, minWatchos)

        logger.debug("Writing debug podspec... ${getPodspecDebug()}")
        getPodspecDebug().write(podspecContentsDebug)
        logger.debug("Writing release podspec... ${getPodspecRelease()}")
        getPodspecRelease().write(podspecContentsRelease)
    }

    @VisibleForTesting
    void validateNumericVersion(String version, String type) {
        // Requires at least a major and minor version number
        Matcher versionMatcher = (version =~ /^[0-9]*(\.[0-9]+)+$/)
        if (!versionMatcher.find()) {
            logger.warn("Non-numeric version for $type: $version")
        }
    }

    // Podspec references are relative to project.buildDir
    @VisibleForTesting
    static String genPodspec(String podname, String publicHeadersDir, String resourceDir,
                             String libName, String j2objcHome,
                             String libDirIos, String libDirOsx, String libDirWatchos,
                             String minIos, String minOsx, String minWatchos) {

        // Absolute paths for Xcode command line
        validatePodspecPath(libDirIos, false)
        validatePodspecPath(libDirOsx, false)
        validatePodspecPath(j2objcHome, false)

        // Relative paths for content referenced by CocoaPods
        validatePodspecPath(publicHeadersDir, false)
        validatePodspecPath(resourceDir, true)

        // TODO: CocoaPods strongly recommends switching from 'resources' to 'resource_bundles'
        // http://guides.cocoapods.org/syntax/podspec.html#resource_bundles

        // TODO: replace xcconfig with {pod|user}_target_xcconfig
        // See 'Split of xcconfig' from: http://blog.cocoapods.org/CocoaPods-0.38/

        // File and line separators assumed to be '/' and '\n' as podspec can only be used on OS X
        return "Pod::Spec.new do |spec|\n" +
               "  spec.name = '$podname'\n" +
               "  spec.version = '1.0'\n" +
               "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'\n" +
               "  spec.resources = '$resourceDir/**/*'\n" +
               "  spec.requires_arc = true\n" +
               "  spec.libraries = " +  // continuation of same line
               "'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', '$libName'\n" +
               "  spec.xcconfig = {\n" +
               "    'HEADER_SEARCH_PATHS' => '$j2objcHome/include $publicHeadersDir'\n" +
               "  }\n" +
               "  spec.ios.xcconfig = {\n" +
               "    'LIBRARY_SEARCH_PATHS' => '$j2objcHome/lib $libDirIos'\n" +
               "  }\n" +
               "  spec.osx.xcconfig = {\n" +
               "    'LIBRARY_SEARCH_PATHS' => '$j2objcHome/lib/macosx $libDirOsx'\n" +
               "  }\n" +
               "  spec.watchos.xcconfig = {\n" +
               "    'LIBRARY_SEARCH_PATHS' => '$j2objcHome/lib $libDirWatchos'\n" +
               "  }\n" +
                // http://guides.cocoapods.org/syntax/podspec.html#deployment_target
               "  spec.ios.deployment_target = '$minIos'\n" +
               "  spec.osx.deployment_target = '$minOsx'\n" +
               "  spec.watchos.deployment_target = '$minWatchos'\n" +
               "  spec.osx.frameworks = 'ExceptionHandling'\n" +
               "end\n"
    }

    @VisibleForTesting
    static void validatePodspecPath(String path, boolean relativeRequired) {
        if (path.contains('//')) {
            throw new InvalidUserDataException("Path shouldn't have '//': $path")
        }
        if (path.endsWith('/')) {
            throw new InvalidUserDataException("Path shouldn't end with '/': $path")
        }
        if (path.endsWith('*')) {
            throw new InvalidUserDataException("Only genPodspec(...) should add '*': $path")
        }
        // Hack to recognize absolute path on Windows, only relevant in unit tests run on Windows
        boolean absolutePath = path.startsWith('/') ||
                               (path.startsWith('C:\\') && Utils.isWindowsNoFake())
        if (relativeRequired && absolutePath) {
            throw new InvalidUserDataException("Path shouldn't be absolute: $path")
        }
        if (!relativeRequired && !absolutePath) {
            throw new InvalidUserDataException("Path shouldn't be relative: $path")
        }
    }
}
