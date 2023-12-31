/*
 * Copyright 2022 Google LLC
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
package com.google.android.libraries.mobiledatadownload;

import android.accounts.Account;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import javax.annotation.concurrent.Immutable;

/** Request to get a single file group. */
@AutoValue
@Immutable
public abstract class GetFileGroupRequest {
  GetFileGroupRequest() {}

  public abstract String groupName();

  public abstract Optional<Account> accountOptional();

  public abstract Optional<String> variantIdOptional();

  public abstract boolean preserveZipDirectories();

  public abstract boolean verifyIsolatedStructure();

  public static Builder newBuilder() {
    return new AutoValue_GetFileGroupRequest.Builder()
        .setPreserveZipDirectories(false)
        .setVerifyIsolatedStructure(true);
  }

  /** Builder for {@link GetFileGroupRequest}. */
  @AutoValue.Builder
  public abstract static class Builder {
    Builder() {}

    /** Sets the name of the file group, which is required. */
    public abstract Builder setGroupName(String groupName);

    /** Sets the account that is associated to the file group, which is optional. */
    public abstract Builder setAccountOptional(Optional<Account> accountOptional);

    /** Sets the variant id associated with the group, which is optional. */
    public abstract Builder setVariantIdOptional(Optional<String> variantIdOptional);

    /**
     * By default, MDD will scan the directories generated by unpacking zip files in a download
     * transform and generate a ClientDataFile for each contained file. By default, MDD also hides
     * the root directory. Setting this to true disables that behavior, and will simply return the
     * directories as ClientDataFiles.
     */
    public abstract Builder setPreserveZipDirectories(boolean preserve);

    /**
     * By default, file groups will isolated structures will have this structure checked for each
     * file when returning the file group. If the isolated structure is not correct, MDD will return
     * a failure.
     *
     * <p>Setting this option to false allows clients to bypass this check, reducing the latency for
     * critical callpaths.
     *
     * <p>For groups that do not have an isolated structure, this option is a no-op.
     *
     * <p>NOTE: All groups with isolated structures are also verified/fixed during MDD's maintenance
     * periodic task.
     */
    public abstract Builder setVerifyIsolatedStructure(boolean verifyIsolatedStructure);

    public abstract GetFileGroupRequest build();
  }
}
