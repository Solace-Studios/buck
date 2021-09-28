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

package com.facebook.buck.util;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class that keeps a list of compiled patterns and provides a method to check whether a
 * string matches at least one of them.
 */
public class PatternsMatcher {

  /** A pattern which patches any string */
  public static final PatternsMatcher ANY = new PatternsMatcher(ImmutableSet.of(), true);
  /** A pattern which matches no string */
  public static final PatternsMatcher NONE = new PatternsMatcher(ImmutableSet.of(), false);

  private final ImmutableSet<Pattern> patterns;
  /** True is like having a pattern {@code .*} in the pattern set */
  private final boolean matchesAny;

  private PatternsMatcher(ImmutableSet<Pattern> patterns, boolean matchesAny) {
    this.patterns = patterns;
    this.matchesAny = matchesAny;
  }

  public PatternsMatcher(ImmutableCollection<String> rawPatterns) {
    this(rawPatterns.stream().map(Pattern::compile).collect(ImmutableSet.toImmutableSet()), false);
  }

  public PatternsMatcher(ImmutableSet<Pattern> compiledPatterns) {
    this(compiledPatterns, false);
  }

  /** @return true if the given string matches some of the patterns */
  public boolean matches(String string) {
    return match(string, true);
  }

  /**
   * @return true if a substring of the given string matches some of the patterns or there are no
   *     patterns
   */
  public boolean substringMatches(String string) {
    return match(string, false);
  }

  private boolean match(String string, boolean fullMatch) {
    if (matchesAny) {
      return true;
    }
    for (Pattern pattern : patterns) {
      Matcher matcher = pattern.matcher(string);
      if (fullMatch ? matcher.matches() : matcher.find()) {
        return true;
      }
    }
    return false;
  }

  /** @return true if this pattern matches any string */
  public boolean isMatchesAny() {
    return matchesAny;
  }

  /** @return true if no string matches this pattern */
  public boolean isMatchesNone() {
    return !matchesAny && patterns.isEmpty();
  }

  /** @return a view of the given map where all non-matching keys are removed. */
  public <V> Map<String, V> filterMatchingMapKeys(Map<String, V> entries) {
    if (matchesAny) {
      return entries;
    }

    return Maps.filterEntries(entries, entry -> matches(entry.getKey()));
  }
}
