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

package com.facebook.buck.parser.api;

import com.facebook.buck.core.exceptions.HumanReadableException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum Syntax {
  PYTHON_DSL,
  SKYLARK,
  ;

  /** Converts a syntax name to a corresponding enum value. */
  public static Optional<Syntax> from(String syntaxName) {
    for (Syntax syntax : values()) {
      if (syntax.name().equals(syntaxName)) {
        return Optional.of(syntax);
      }
    }
    return Optional.empty();
  }

  /** Parse enum value or throw {@link com.facebook.buck.core.exceptions.HumanReadableException}. */
  public static Syntax parseOrThrowHumanReadableException(String syntax) {
    return from(syntax.toUpperCase(Locale.ROOT))
        .orElseThrow(
            () ->
                new HumanReadableException(
                    "unknown syntax: '"
                        + syntax
                        + "'; possible values: "
                        + Arrays.stream(Syntax.values())
                            .map(syntax1 -> "'" + syntax1.name() + "'")
                            .collect(Collectors.joining(", "))));
  }
}
