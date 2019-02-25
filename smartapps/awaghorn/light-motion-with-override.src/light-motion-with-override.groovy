/**
 *  Created by Andrew Waghorn
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
		name: "Light motion with override",
		namespace: "awaghorn",
		author: "Andrew Waghorn",
		description: "Trigger light from motion with ability to override",
		category: "Convenience",
		iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/smartlights.png",
		iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/smartlights@2x.png",
		pausable: true
)

preferences {
	page(name: "prefsPage")
}

def prefsPage() {
	dynamicPage (name: "prefsPage", title: "Main Configuration", install: true, uninstall: true) {
    
    	section("Choose light to be triggered"){
        	input("light", "capability.switch", title: "Light to turn on", required: true, multiple: false)
        }

		section("Choose sensor(s) to trigger light activiation"){
        	input("sensors", "capability.motionSensor", title: "Turn on light when motion sensed at", required: true, multiple: true)
        }
    
        section("Choose button to override motion sensing"){
            input "button", "capability.button", title: "Override button", required: true, multiple: false, submitOnChange: true
        }
        
        if (button) {
			section("Button Event") {
        		input("buttonEvent", "enum", title: "Select Button Event to trigger override", required: false, multiple: false, options: prefEvents(button))
                input("buttonReset", "bool", title: "Reset override if button held (requires event to report string 'held' in message)", required: false, defaultValue: false)
                input("buttonResetAuto", "bool", title: "Automatically reset daily", defaultValue: true)
                input("buttonResetAutoTime", "time", title: "Automatically reset at what time?")
	        }
        }
 
        section("Turn off as well?") {
                input("offAfterMotion", "bool", title: "Turn off when motion stops?", required: false, defaultValue: false)
				input("offAfterMotionMins", "number", title: "After how many minutes?", required: false)
        }
        
        section("Only run SmartApp when...") {
            input("limitSwitch", "capability.switch", title: "Only if switch...", required: false) 
            input("limitSwitchState", "enum", title: "...is...", required: false, options: ["on","off"])
			mode(title: "Run only for specific mode(s)", required: false)
		}
        
         section("Only run SmartApp between...") {
            input("limitStart", "time", title: "Start Time", required: false) 
            input("limitEnd", "time", title: "End Time", required: false)
		}
        
        section("Enter name for automation") {
            label(title: "Assign a name", required: true)

        }
    }
}

def prefEvents(device) {

    def returnArr = []
    device.supportedAttributes.each { n-> 
    returnArr << "$n"    
    }
    //log.debug("populated array $returnArr")
    return returnArr
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
	subscribe(button, "$buttonEvent", buttonHandler)
    subscribe(sensors, "motion", motionHandler)
    subscribe(light, "switch", lightHandler)
    //(re)set override state
    overrideReset()
    if (buttonResetAuto && buttonResetAutoTime) {
    	schedule(buttonResetAutoTime, overrideReset)
    }
}

def buttonHandler(evt) {
	//Use state variable to decide if to override or not
	//log.debug "Notify got evt ${evt}"
   	log.debug "$evt.name:$evt.value"
	
    if (buttonReset && (evt.value.contains("held") || evt.value.contains("released"))) {
    	//Held buttons also report a release which causes us to cancel the setting here if not captured
    	state["override"] = false
    } else {
    	//Set override if not held or not using that functionality
        state["override"] = true
    }
    log.debug("Override set to ${state.override}")
}

def motionHandler(evt) {
	
    log.debug "Motion handler: $evt.name:$evt.value"
       
    //Check settings defined and if so apply check, negating as we want the check to be TRUE if we DON'T want to run
    def limitSwitchCheck = (limitSwitch && limitSwitchState && !limitSwitch.currentValue("switch") == limitSwitchState)
    def limitTimeCheck = (limitStart && limitEnd && !timeOfDayIsBetween(limitStart, limitEnd, new Date(), location.timeZone))
	
    //only run if light is off  and we are active or vice versa
    def selfCheckMap = [active: "off", inactive: "on"]
    def limitSelfCheck = !(light && light.currentValue("switch") == selfCheckMap[evt.value])

    if (!limitSwitchCheck && !limitTimeCheck && !state.override && !limitSelfCheck) {
    	if (evt.value == "active") {
			light.on()        
        } else if (evt.value == "inactive") {
        	if (offAfterMotion && offAfterMotionMins) {
            	//Check ALL motion senors are inactive
                def alloff = true
                sensors.each { s-> 
                	alloff = s.currentValue("motion") == "inactive"
                }
                if (alloff) {
                    runIn(offAfterMotionMins*60,lightOffDelay)
                }
            }
        } else {
        	log.debug("Unhandled event value with value $evt.value")
        }
    
    } else {
    	log.debug("No action taken, limitSwitchCheck: $limitSwitchCheck ; limitTimeCheck: $limitTimeCheck ; override = ${state.override} ; Self check: $limitSelfCheck")
    }
  
}

def lightHandler(evt) {
	//If light changes state then unschedule auto off
    //On basis if turned on then want to cancel planned disable and if turned off no need to do
   	log.debug "Light handler $evt.name:$evt.value"
	
    if (offAfterMotion && offAfterMotionMins) {
    	lightActUnsch()
    }
}

def overrideReset() {
	state.override = false
    log.debug("override has been auto reset")
    
}

def lightOffDelay() {
	//Turn light off after delay (from schedule)
    light.off()
}

def lightActUnsch() {
	//Cancel schedule for light
    unschedule(lightOffDelay)
}