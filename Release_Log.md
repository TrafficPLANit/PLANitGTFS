# Release Log

This project contains PLANit GTFS code to allow for a lightweight memory model accessing GTFS file data

## 0.4.0

**Enhancements**

* [General] Support for producing service networks and routed services as well as transfer zones in a stable-ish manner in PLANit memory model format
* [General] Support for chaining other intermodal readers to construct networks and transfer zones before overlaying GTFS
* #15 When preferred connectoid access link segment is the exit segment of the access node, make sure allow all entry segments (of other links) as eligible
* #14 Automatically deactivate modes if the physical network is found to be not able to support them based on track type (road/rail/water)
* #12 update to junit5
* #10 Support multiple modes being serviced by the same GTFS stop
* #9 Remove unused transfer zones as optional setting, to be sure to remove clutter if needed
* #7 Add support for filtering by time of day (time period)
* #6 Add support for filtering GTFS data by day
* #5 Support consolidation of departures
* #3 Add support for CI by adding github actions and run tests on push commit

(issues in OSM repo, but pertaining to GTFS, stemming from time when GTFS repo was not yet split off)
* #38 GTFS support - STEP 4 - Parse GTFS schedules and map them to stops in PLANit memory model
* #36 Add option to Add GTFS stops without matching OSM/existing PLANit stops within parser bounding box
* #35 Combine matching GTFS stops with existing (OSM/PLANit) stops


**Bug fixes**

* #13 GTFS connectoid identification not correct after PLANit node has been identified in case of RAIL based mode

## 0.3.0

* added support for warning messages conditional on the availability requirements of the GTFS file #1
* update packages to conform to new domain org.goplanit.* #2

