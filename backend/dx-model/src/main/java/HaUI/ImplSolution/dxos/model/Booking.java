// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

// What a reservation process needs to know: the field that names the booked resource, the fields
// that hold the start and the end, and how many bookings may overlap on one resource.
public record Booking(String resource,String starts,String ends,int capacity){}
