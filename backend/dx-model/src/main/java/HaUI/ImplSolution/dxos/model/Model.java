// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// A parsed and validated model file.
public record Model(int format,String name,List<Role>roles,List<Unit>units,Map<String,Entity>entities){

public record Role(String name,String description,boolean staff){}

// An organisational unit; its members are the Keycloak group of the same name.
public record Unit(String name,String label){}

public record Entity(String name,String label,Map<String,Field>fields,Map<String,Scope>access,Process process,String ownerVia){

public Optional<Field>field(String fieldName){return Optional.ofNullable(fields.get(fieldName));}

// The field that names the unit a record belongs to.
public Optional<Field>unitField(){return fields.values().stream().filter(f->f.type()==FieldType.UNIT).findFirst();}}

// renamedFrom names the column this field had in the version before; defaultValue fills existing
// and new rows.
public record Field(String name,FieldType type,String label,boolean required,boolean unique,List<String>options,String to,String renamedFrom,String defaultValue){}

// The process family an entity runs, the role that handles its records, whether the creator of a
// record may handle it, and how long a handler has before a record counts as overdue (null for no
// deadline). After maps a final state to the actions taken when a record reaches it.
public record Process(String family,String handler,boolean allowSelfDecision,Duration deadline,Map<String,List<Completion>>after){

public ProcessFamily definition(){return ProcessFamily.find(family).orElseThrow();}}

// Name of the PostgreSQL schema that holds the tables of this model.
public String schema(){return"m_"+name;}

public Optional<Entity>entity(String entityName){return Optional.ofNullable(entities.get(entityName));}}
