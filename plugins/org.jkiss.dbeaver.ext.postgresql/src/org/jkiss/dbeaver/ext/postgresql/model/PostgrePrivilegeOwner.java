/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2021 DBeaver Corp and others
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

package org.jkiss.dbeaver.ext.postgresql.model;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.access.DBAPrivilegeOwner;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.rdb.DBSCatalog;

import java.util.Collection;

/**
 * PostgrePrivilegeOwner
 */
public interface PostgrePrivilegeOwner extends PostgreObject, DBAPrivilegeOwner {

    PostgreSchema getSchema();

    PostgreRole getOwner(DBRProgressMonitor monitor) throws DBException;

    /**
     * Get object privileges.
     * @param includeNestedObjects - include permissions for all nested objects. For example for table columns.
     */
    @Override
    Collection<PostgrePrivilege> getPrivileges(DBRProgressMonitor monitor, boolean includeNestedObjects, DBSCatalog catalog) throws DBException;

    String generateChangeOwnerQuery(String owner);
}
