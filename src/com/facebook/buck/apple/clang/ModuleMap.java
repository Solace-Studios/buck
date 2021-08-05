/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.apple.clang;

import com.google.common.base.Objects;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Class to create module header tree for rendering. */
class Module {
  public Path name;
  public Path header;
  public Map<Path, Module> submodules;

  Module(Path name) {
    this.name = name;
    this.submodules = new HashMap<>();
  }

  Module getSubmodule(Path name) {
    Module submodule = submodules.get(name);
    if (submodule == null) {
      submodule = new Module(name);
      submodules.put(name, submodule);
    }
    return submodule;
  }

  void render(StringBuilder s, boolean requiresCxx, int level) {
    indent(s, level).append("module ").append(name).append(" {\n");

    // Module names can only include letters, numbers and underscores, and may not start with a
    // number.
    HashSet<String> submoduleNames = new HashSet<>();
    submodules.keySet().stream()
        .sorted()
        .forEach(
            name -> {
              Module submodule = submodules.get(name);
              String sanitized = name.toString();

              // A leaf submodule name is a filename, remove its extension for readability
              if (submodule.submodules.isEmpty()) {
                int extensionIndex = sanitized.lastIndexOf('.');
                if (extensionIndex > 0) {
                  sanitized = sanitized.substring(0, extensionIndex);
                }
              }

              sanitized = sanitized.replaceAll("[^A-Za-z0-9_]", "_");
              if (Character.isDigit(sanitized.charAt(0))) {
                sanitized = "_" + sanitized;
              }

              // Its possible to have collisions either from matching filenames with different
              // extensions or with matching names with differing invalid characters. Keep adding
              // underscores until we have a unique module name.
              while (submoduleNames.contains(sanitized)) {
                sanitized += "_";
              }
              submoduleNames.add(sanitized);
              submodule.name = Paths.get(sanitized);
              submodule.render(s, false, level + 1);
            });

    if (header != null) {
      indent(s, level + 1).append("header \"").append(header).append("\"\n");
      indent(s, level + 1).append("export *\n");
    }
    if (requiresCxx) {
      indent(s, level + 1).append("requires cplusplus\n");
    }
    indent(s, level).append("}\n");
  }

  private StringBuilder indent(StringBuilder s, int level) {
    for (int i = 0; i < level; i++) {
      s.append("\t");
    }
    return s;
  }
}

/**
 * Creates a modulemap file that uses an explicit `header` declaration for each header in the
 * module.
 */
public class ModuleMap {
  /**
   * Modulemaps can optionally include the "-Swift.h" header generated by the Swift compiler.
   *
   * <p>There are two reasons to exclude this header from a modulemap. The simple case is if the
   * target does not contain Swift code. The second case is when compiling the Swift code of the
   * module, as the header cannot exist until the Swift code has been compiled.
   */
  public enum SwiftMode {
    NO_SWIFT,
    INCLUDE_SWIFT_HEADER;

    /** @return `true` if the rendered modulemap should include the "-Swift.h" header. */
    public boolean includeSwift() {
      switch (this) {
        case NO_SWIFT:
          return false;
        case INCLUDE_SWIFT_HEADER:
          return true;
      }

      throw new RuntimeException();
    }
  }

  private String moduleName;
  private Set<Path> headers;
  private SwiftMode swiftMode;
  private boolean useSubmodules;
  private boolean requiresCplusplus;

  ModuleMap(
      String moduleName,
      SwiftMode swiftMode,
      Set<Path> headers,
      boolean useSubmodules,
      boolean requiresCplusplus) {
    this.moduleName = moduleName;
    this.swiftMode = swiftMode;
    this.headers = headers;
    this.useSubmodules = useSubmodules;
    this.requiresCplusplus = requiresCplusplus;
  }

  /**
   * Creates a module map.
   *
   * @param moduleName The name of the module.
   * @param headerPaths The exported headers of the module.
   * @param swiftMode Whether or not to include the "-Swift.h" header in the modulemap.
   * @param requiresCplusplus Whether or not to include "requires cplusplus" in the modulemap.
   * @return A module map instance.
   */
  public static ModuleMap create(
      String moduleName,
      SwiftMode swiftMode,
      Set<Path> headerPaths,
      boolean useSubmodules,
      boolean requiresCplusplus) {
    return new ModuleMap(moduleName, swiftMode, headerPaths, useSubmodules, requiresCplusplus);
  }

  /**
   * Creates a module map.
   *
   * @param moduleName The name of the module.
   * @param headerPaths The exported headers of the module.
   * @param swiftMode Whether or not to include the "-Swift.h" header in the modulemap.
   * @return A module map instance.
   */
  public static ModuleMap create(
      String moduleName, SwiftMode swiftMode, Set<Path> headerPaths, boolean useSubmodules) {
    return ModuleMap.create(moduleName, swiftMode, headerPaths, useSubmodules, false);
  }

  /**
   * Renders the modulemap to a string, to be written to a .modulemap file.
   *
   * @return A string representation of the modulemap.
   */
  public String render() {
    StringBuilder s = new StringBuilder();
    if (useSubmodules) {
      renderSubmodules(s);
    } else {
      renderSingleModule(s);
    }

    if (swiftMode.includeSwift()) {
      s.append("\nmodule ")
          .append(moduleName)
          .append(".Swift {\n\theader \"")
          .append(moduleName)
          .append("/")
          .append(moduleName)
          .append("-Swift.h\"\n\trequires objc\n}\n");
    }

    return s.toString();
  }

  private void renderSubmodules(StringBuilder s) {
    // Create a tree of nested Module, one for each path component.
    Module rootModule = new Module(Paths.get(moduleName));
    for (Path header : headers) {
      Module module = rootModule;
      for (int i = 0; i < header.getNameCount(); i++) {
        Path component = header.getName(i);
        if (i == 0 && component.equals(rootModule.name)) {
          // The common case is we have a single header path prefix that matches the module name.
          // In that case add the headers directly to the top level module.
          continue;
        }
        module = module.getSubmodule(component);
      }
      module.header = header;
    }

    rootModule.render(s, requiresCplusplus, 0);
  }

  private void renderSingleModule(StringBuilder s) {
    s.append("module ").append(moduleName).append(" {\n");
    headers.stream()
        .sorted()
        .forEach(p -> s.append("\theader \"").append(p.toString()).append("\"\n"));
    s.append("\texport *\n");
    if (requiresCplusplus) {
      s.append("\trequires cplusplus\n");
    }
    s.append("}\n");
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ModuleMap)) {
      return false;
    }

    ModuleMap other = (ModuleMap) obj;
    return Objects.equal(moduleName, other.moduleName)
        && Objects.equal(headers, other.headers)
        && Objects.equal(swiftMode, other.swiftMode)
        && useSubmodules == other.useSubmodules
        && Objects.equal(requiresCplusplus, other.requiresCplusplus);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(moduleName, headers, swiftMode, requiresCplusplus);
  }
}
