/**
 *  SIR 321 Timer
 *
 *  Copyright 2019 Andrew Waghorn
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

metadata {
	definition (name: "SIR 321 Timer", namespace: "awaghorn", author: "Andrew Waghorn") {
		capability "Actuator"
		capability "Switch"
        capability "Refresh"
        capability "Polling"
        
        command("scheduleCheck")
        command("setSchedule", ["number"])
        command("timeStepUp")
        command("timeJumpUp")
        command("timeStepDown")
        command("timeJumpDown")
        
        attribute "scheduleDuration", "number"
        attribute "scheduleDurationDisp", "string"
        attribute "scheduleOperating", "string"
        
        fingerprint type: "1000", mfr: "0059", prod: "0010"
	}


	simulator {
	}

	tiles (scale: 2) {
		multiAttributeTile(name: "switch", type: "generic", width: 6, height: 4, canChangeIcon: true, decoration: "flat") {
        	tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
                attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00a0dc"
        	}
            tileAttribute("device.scheduleOperating", key: "SECONDARY_CONTROL") {
            	attributeState "scheduleOperating", defaultState: true, label: '${currentValue}' 
            }
        }
        standardTile("refresh", "device.switch", decoration: "flat", width: 2, height: 2) {
        	state "default", label:"", action:"refresh", icon:"st.secondary.refresh"
        }
        valueTile("runSchedule", "device.scheduleDurationDisp", decoration: "flat", width: 2, height: 2) {
        	state "run", defaultState: true, label:'${currentValue}', action:"setSchedule"
        }
        standardTile("timeStepUp", "device.scheduleDuration", decoration: "flat", width: 1, height: 1) {
        	state "scheduleDuration", defaultState: true, label: "", icon: "st.samsung.da.oven_ic_plus", action:"timeStepUp"
        }
        standardTile("timeJumpUp", "device.scheduleDuration", decoration: "flat", width: 1, height: 1) {
        	state "scheduleDuration", defaultState: true, label: "", icon: "st.thermostat.thermostat-up", action:"timeJumpUp"
        }
        standardTile("timeStepDown", "device.scheduleDuration", decoration: "flat", width: 1, height: 1) {
        	state "scheduleDuration", defaultState: true, label: "", icon: "st.samsung.da.oven_ic_minus", action:"timeStepDown"
        }
        standardTile("timeJumpDown", "device.scheduleDuration", decoration: "flat", width: 1, height: 1) {
        	state "scheduleDuration", defaultState: true, label: "", icon: "st.thermostat.thermostat-down", action:"timeJumpDown"
        }
        
        main(["switch"])
        details(["switch","runSchedule","timeStepUp","timeJumpUp","refresh","timeStepDown","timeJumpDown"])
	}
    
    preferences {
        input "StepSize", "number", title: "Small Step Size", description: "Adjust schedule by this many minutes for small steps (+/-) buttons",
              range: "0..60", displayDuringSetup: false
        input "JumpSize", "number", title: "Large Step Size", description: "Adjust schedule by this many minutes for large steps (^/v) buttons",
              range: "10..1440", displayDuringSetup: false
    }
}

def parse(String description) {
	log.debug "Parsing '${description}' which parses to ${zwave.parse(description,getSupportedCommands())}"
    def event
    def results = []
	

    if (description.startsWith("Err")) {
        log.debug("An error has occurred")
    } else { 
        def cmd = zwave.parse(description)
        //log.debug "Parsed Command: $cmd"
        if (cmd) {
            event = zwaveEvent(cmd)
            if (event) {
                results += event
            }
		}
    }
    return results

}

def installed() {
		
}

def updated() {
	//Set initial schedule duration if required 
    if (!device.currentValue("scheduleDuration")) {
    	sendEvent(name: "scheduleDuration", value: 30, isStateChange: true, displayed: false)
        sendEvent(name: "scheduleDurationDisp", value: "Run for\n${fmtduration(30)}", isStatechange: true, displayed: false)
    }
}

def refresh() {
	//log.debug "Running refresh"

	def cmds = []
    
    cmds << zwave.switchBinaryV1.switchBinaryGet().format()
    cmds << new physicalgraph.zwave.commands.schedulev1.CommandScheduleGet(scheduleId: 0x01).format()
    
    return delayBetween(cmds,100)
}

def poll() {
	log.debug("Poll called")
    refresh()
}

// handle commands
def on() {
	//log.debug "Executing 'on'"
	def cmds = []
    
    cmds << zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF).format()
    //Add request for report to check status
    cmds << zwave.switchBinaryV1.switchBinaryGet().format()
    
    return delayBetween(cmds,1000)
}

def off() {
	//log.debug "Executing 'off'"
	def cmds = []
    
    cmds << zwave.switchBinaryV1.switchBinarySet(switchValue: 0x00).format()
    //Add request for reports to check status
    cmds << zwave.switchBinaryV1.switchBinaryGet().format()
    cmds << new physicalgraph.zwave.commands.schedulev1.CommandScheduleGet(scheduleId: 0x01).format()
    
    return delayBetween(cmds,200)
}

def reportSwitchStatus(physicalgraph.zwave.Command cmd) {

    def result = []

	def switchValue = (cmd.value ? "on" : "off")
    def switchEvent = createEvent(name: "switch", value: switchValue)
    result << switchEvent

    return result
}

//Commands for tile presses

void timeStepUp() {
	def step = StepSize ?: 5
    fmtschedule(step)
}

void timeJumpUp() {
	def step = JumpSize ?: 30
    fmtschedule(step)
}

void timeStepDown() {
	def step = StepSize ?: 5
    fmtschedule(-1 * step)
}

void timeJumpDown() {
	def step = JumpSize ?: 30
    fmtschedule(-1 * step)
}

def setSchedule(Integer duration) {
	
    def runtime = device.currentValue("scheduleDuration") ?: 30 //default to 30 to handle null
    runtime = duration ?: runtime //Override event based value (from DTH Smartapp) with value passed in to allow use from Smartapps
    def cmds = []
    
	//ST does not add the command correctly so has to be done manually. Need to add command preceeded by command byte count (=3)

    def cmdSchedule = zwave.scheduleV1.commandScheduleSet(scheduleId: 0x01, startYear: 0xFF, startHour: 0x1F, startMinute: 0x3F, durationByte: runtime.toInteger(), durationType: 0x00, numberOfCmdToFollow: 0x01).format()
    def cmdSwitch = zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF).format()
    cmds << "${cmdSchedule}03${cmdSwitch}"
    //get reports to update GUI
    cmds << zwave.switchBinaryV1.switchBinaryGet().format()
    cmds << new physicalgraph.zwave.commands.schedulev1.CommandScheduleGet(scheduleId: 0x01).format()
    
    return delayBetween(cmds,200)
}

/**
ZWave overloaded functions
**/

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    return reportSwitchStatus(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    return reportSwitchStatus(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.schedulev1.CommandScheduleReport cmd) {
    
    def eventtext
    if (cmd.durationByte == 0) {
    	eventtext = "Timer not operating"
        unschedule(scheduleCheck) //cancel 5 minute runs of refresh trigger on schedule set
    } else {
    	eventtext = "Timer operating, ${fmtduration(cmd.durationByte)} remaining"
    }    
    return createEvent(name: "scheduleOperating", value: "$eventtext", descriptionText: "$eventtext", isStateChange: true)
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    log.debug("Other unknown event: $cmd")
}

/**********************************************************************

Private functions for DTH

*/

private fmtduration(duration) {
	def dur = duration.toInteger()
    def durtext
    if (dur) {
        durtext = dur >= 60 ? "${dur.intdiv(60)} h " : ""
        durtext += (dur%60) == 0 ? "" : "${dur%60} min"
    } else {
    	durtext = "error"
    }
    return durtext
}

private fmtschedule(increment) {
	
    def rtnEvents = []
    
    //default to 30 (should not be required but as safety check) and increment to 5 if not set
    def newDuration = device.currentValue("scheduleDuration") ?: 30
    def adj = increment ?: 5
    newDuration += adj
    newDuration = (newDuration <= 0) ? 5 : newDuration
    newDuration = (newDuration > 1440) ? 1440 : newDuration
    
    sendEvent(name: "scheduleDuration", value: "${newDuration}", isStateChange: true, displayed: false)
    sendEvent(name: "scheduleDurationDisp", value: "Run for\n${fmtduration(newDuration)}", isStatechange: true, displayed: false)
    
}

/**
 *  getSupportedCommands() - Returns a map of the command versions supported by the device.
 *
 *  Used by parse()
 *
 *  The SIR 321 supports the following commmand classes:

  (0x85)      Association V1
  (0x20)      Basic V1
  (0x25)      Binary Switch V1
  (0x70)      Configuration V1
  (0x72)      Manufacturer Specific V1
  (0x53)      Schedule V1
  (0x86)      Version V1
 
 *
 **/
private getSupportedCommands() {
    return [0x20: 1, 0x25: 1, 0x53: 1, 0x70: 1, 0x72: 1, 0x85: 1, 0x86: 1]
}