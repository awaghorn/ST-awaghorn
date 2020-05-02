/**
 *  Fan for Laundry
 *
 *  Copyright 2020 Andrew Waghorn
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Fan for Laundry",
    namespace: "awaghorn",
    author: "Andrew Waghorn",
    description: "App to notify you if a sensor has not been activated by a certain time",
    category: "My Apps",
    iconUrl: "http://cdn.device-icons.smartthings.com/Appliances/appliances1-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Appliances/appliances1-icn@2x.png",
    iconX3Url: "http://cdn.device-icons.smartthings.com/Appliances/appliances1-icn@2x.png")


preferences {
	page(name: "prefsPage")
}

def prefsPage() {
	dynamicPage (name: "prefsPage", title: "Main Configuration", install: true, uninstall: true) {
          
        section("Laundry Devices"){
            input "triggerDryer", "capability.dryerOperatingState", title: "Select Tumble Dryer", required: false, multiple: false
            input "triggerWasher", "capability.washerOperatingState", title: "Select Washing Machine", required: false, multiple: false
        	paragraph "Make sure at least one washer OR drier is selected"        
        }      
        section("Trigger this fan"){
            input "outputFan", "capability.switch", title: "Select fan to operate", required: true, multiple: false
        }
        section("Name app and configure modes") {
            label(title: "Assign a name", required: true)
            mode(title: "Run only for specific mode(s)", required: false)

        }
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	subscribeToEvents()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	subscribeToEvents()
}

def subscribeToEvents() {
	log.debug("Subscribing to events")
	if (triggerDryer) {
    	log.debug("Subscribing to dryer")
    	subscribe(triggerDryer, "machineState", eventHandler)
        //log.debug("Dryer device is: ${triggerDryer.getId()}")
    }
    if (triggerWasher) {
    	log.debug("Subscribing to washer")
    	subscribe(triggerWasher, "machineState", eventHandler)
        //log.debug("Washer device is: ${triggerWasher.getId()}")
        
    }
}

def eventHandler(evt) {
	/*log.debug "Notify got evt ${evt}"
    log.debug "Event value: ${evt.value}"
    log.debug "Event name: ${evt.name}"
    log.debug "Event descriptionText: ${evt.descriptionText}"
    log.debug "Event displayName: ${evt.displayName}"
    log.debug "Event is change?: ${evt.isStateChange()}"
	*/
    
    if (evt.isStateChange()) {
    	//If change of event and running then turn fan on if not already operating
    	if (evt.value == "run" && outputFan.currentSwitch == "off") {
        	outputFan.on()
        }
        
        //If change of event and turning off then turn off unless other device is running
    	//First work out the status of the other device
        def otherDevState = "undef"
        if (triggerDryer && evt.device.id == triggerDryer.getId() && triggerWasher) {
        	otherDevState = triggerWasher.currentMachineState
        } else if (triggerWasher && evt.device.id == triggerWasher.getId() && triggerDryer) {
        	otherDevState = triggerDryer.currentMachineState
        }
        /*log.debug("CROSS CHECK OF STATUS")
        log.debug("Other State set as: $otherDevState")
        log.debug("Event device is: ${evt.device.id}")
        log.debug("Washer Status reported as: ${triggerWasher.currentMachineState}")
        log.debug("Dryer Status reported as: ${triggerDryer.currentMachineState}")
        */
        if (evt.value != "run" && otherDevState != "run" && outputFan.currentSwitch == "on") {
        	outputFan.off()
        }
    }

}