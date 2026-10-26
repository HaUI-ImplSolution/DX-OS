// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.query;

import java.time.Duration;
import java.time.Instant;

// How a chart cuts a span of time into columns: the shorter the span, the finer the columns. The
// columns line up with local midnight (Asia/Ho_Chi_Minh, UTC+7, no daylight saving), so hourly
// columns start on the hour and six-hour columns at 00:00, 06:00, 12:00 and 18:00.
//
// interval: a constant of this class, so it is safe to place in SQL text
public record Bucketing(long seconds,String interval){

// The origin of date_bin, at local midnight.
public static final String ORIGIN="TIMESTAMPTZ '1970-01-01 00:00:00+07'";

private static final long LOCAL_OFFSET_SECONDS=7*3600;

public static Bucketing forSpan(Duration span){if(span.compareTo(Duration.ofHours(1))<=0){return new Bucketing(60,"1 minute");}if(span.compareTo(Duration.ofHours(6))<=0){return new Bucketing(300,"5 minutes");}if(span.compareTo(Duration.ofHours(48))<=0){return new Bucketing(3600,"1 hour");}return new Bucketing(21600,"6 hours");}

// The start of the column holding the instant.
public Instant start(Instant at){long local=at.getEpochSecond()+LOCAL_OFFSET_SECONDS;return Instant.ofEpochSecond(Math.floorDiv(local,seconds)*seconds-LOCAL_OFFSET_SECONDS);}}
