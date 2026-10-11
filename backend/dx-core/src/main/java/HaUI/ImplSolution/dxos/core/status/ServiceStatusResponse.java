// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.status;

import java.util.List;

public record ServiceStatusResponse(List<Service>services){

public record Service(String name,String status){}}
