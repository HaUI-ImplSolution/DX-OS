// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// A parsed and validated model file.
public record Model(int format,String name,List<Role>roles,Map<String,Entity>entities){

public record Role(String name,String description,boolean staff){}

public record Entity(String name,String label,Map<String,Field>fields,Map<String,Scope>access,Process process,String ownerVia){

public Optional<Field>field(String fieldName){return Optional.ofNullable(fields.get(fieldName));}}

public record Field(String name,FieldType type,String label,boolean required,boolean unique,List<String>options,String to){}

// The workflow template an entity runs, the role that decides, and whether the creator of a
// record may decide it.
public record Process(String template,String approver,boolean allowSelfDecision){}

// Name of the PostgreSQL schema that holds the tables of this model.
public String schema(){return"m_"+name;}

public Optional<Entity>entity(String entityName){return Optional.ofNullable(entities.get(entityName));}}
