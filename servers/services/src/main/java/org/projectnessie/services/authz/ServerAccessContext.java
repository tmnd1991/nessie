/*
 * Copyright (C) 2020 Dremio
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
package org.projectnessie.services.authz;

import java.security.Principal;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable(prehash = true)
public abstract class ServerAccessContext implements AccessContext {

  @Override
  public abstract String operationId();

  @Override
  @Nullable
  public abstract Principal user();

  public static ServerAccessContext of(String operationId, Principal principal) {
    return ImmutableServerAccessContext.builder().operationId(operationId).user(principal).build();
  }
}
