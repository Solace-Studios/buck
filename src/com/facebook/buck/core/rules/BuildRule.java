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

package com.facebook.buck.core.rules;

import com.facebook.buck.core.build.action.BuildEngineAction;
import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rulekey.AllowsNonAnnotatedFields;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.step.Step;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import java.util.SortedSet;
import javax.annotation.Nullable;

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE)
public interface BuildRule
    extends Comparable<BuildRule>, AllowsNonAnnotatedFields, BuildEngineAction, HasNameAndType {
  // We allow non-@AddToRuleKey annotated fields in BuildRule because they are so extensively used
  // for non-action-y things (like Provider-type things).

  @Override
  BuildTarget getBuildTarget();

  @Override
  default String getFullyQualifiedName() {
    return getBuildTarget().getFullyQualifiedName();
  }

  default String toStringWithConfiguration() {
    return getBuildTarget().toStringWithConfiguration();
  }

  /**
   * @return the set of rules that must be built before this rule. Normally, this matches the value
   *     of the {@code deps} argument for this build rule in the build file in which it was defined.
   *     <p>However, there are special cases where other arguments pull in implicit dependencies
   *     (e.g., the {@code keystore} argument in {@code android_binary}). In these cases, the
   *     implicit dependencies are also included in the set returned by this method. The value of
   *     the original {@code deps} argument, as defined in the build file, must be accessed via a
   *     custom getter provided by the build rule.
   */
  SortedSet<BuildRule> getBuildDeps();

  /** @return the same value as {@link #getFullyQualifiedName()} */
  @Override
  String toString();

  ImmutableList<? extends Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext);

  @Nullable
  SourcePath getSourcePathToOutput();

  /**
   * @return true if the output of this build rule is compatible with {@code buck build --out}. To
   *     be compatible, that means (1) {@link #getSourcePathToOutput()} cannot return {@code null},
   *     and (2) the output file works as intended when copied to an arbitrary path (i.e., does not
   *     have any dependencies on relative symlinks).
   */
  default boolean outputFileCanBeCopied() {
    return getSourcePathToOutput() != null;
  }

  ProjectFilesystem getProjectFilesystem();

  /**
   * Whether this {@link BuildRule} can be cached.
   *
   * <p>Uncached build rules are never written out to cache, never read from cache, and does not
   * count in cache statistics. This rule is useful for artifacts which cannot be easily normalized.
   *
   * <p>Uncached rules are not always rebuilt, however, as long as the existing on-disk
   * representation is up to date. This means that these rules can take advantage of {@link
   * com.facebook.buck.core.rules.attr.SupportsInputBasedRuleKey} to prevent rebuilding.
   */
  @Override
  @JsonIgnore
  boolean isCacheable();

  /** Whether this {@link BuildRule} may have any steps to build. */
  boolean hasBuildSteps();

  @Override
  default int compareTo(BuildRule that) {
    if (this == that) {
      return 0;
    }

    return this.getBuildTarget().compareTo(that.getBuildTarget());
  }

  /**
   * Updates the BuildRuleResolver and associated objects for this build rule.
   *
   * <p>Build rules sometimes hold field references to build rule resolvers. If this build rule is
   * to be cached, it must update its BuildRuleResolver when a new action graph is constructed to
   * avoid leaking the entire action graph it was originally associated with.
   */
  void updateBuildRuleResolver(BuildRuleResolver ruleResolver);

  /**
   * @return true if this rule should only be allowed to be executed via Remote Execution if it
   *     satisfies input size limits.
   */
  @Override
  default boolean shouldRespectInputSizeLimitForRemoteExecution() {
    return true;
  }
}
