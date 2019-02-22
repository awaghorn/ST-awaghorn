/**
 *  Hank HKZW-SCN04 DTH by Andrew Waghorn
 *  Based on Hank HKZW-SCN04 DTH by Emil Åkered (@emilakered)
 *  Based on DTH "Fibaro Button", copyright 2017 Ronald Gouldner (@gouldner)
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
 *	2019-02-22
 *	- Initial release
 *
 */
 
metadata {
    definition (name: "Hank Four-key Scene Controller", namespace: "awaghorn", author: "Andrew Waghorn") {
        capability "Actuator"
        capability "Sensor"
        capability "Battery"
        capability "Button"
        capability "Configuration"
        capability "Holdable Button" 
        
        attribute "lastPressed", "string"
        attribute "lastPressedat", "string"
		attribute "numberOfButtons", "number"
		attribute "lastSequence", "number"
        
        (1..4).each{ n->
        	attribute "button${n}name", "string"
            attribute "button${n}status", "string"
            command	  "buttonpush$n"
        }
        
	fingerprint mfr: "0208", prod: "0200", model: "000B"
        fingerprint deviceId: "0x1801", inClusters: "0x5E,0x86,0x72,0x5B,0x59,0x85,0x80,0x84,0x73,0x70,0x7A,0x5A", outClusters: "0x26"
    }

    simulator {
    }

    tiles (scale: 2) {      
        def detailList = []
        multiAttributeTile(name:"button", type:"generic", width:6, height:4, canChangeIcon:true) {
            tileAttribute("device.lastPressed", key: "PRIMARY_CONTROL"){
				attributeState "default", label:'${currentValue}'
            }
			tileAttribute("device.lastPressedat", key: "SECONDARY_CONTROL") {
                attributeState "default", label:'Last used: ${currentValue}'
            }
        }
        detailList << "button"
        
/*		valueTile("button1", "device.lastPressed", width: 3, height: 1, decoration: "flat") {
        	state "val", label:'$button1name last used: ${currentValue}', backgroundColor:"#ffffff", defaultState: true
        }
  */      
        (1..4).each{ n->
			valueTile("button$n", "device.button${n}status", width: 3, height: 1, decoration: "flat") {
            	state "val", label:'${currentValue}', backgroundColor:"#ffffff", defaultState: true, action: "buttonpush${n}"
            }
            detailList << "button$n"
        }
        
        valueTile("battery", "device.battery", decoration: "flat", width: 3, height: 1){
			state "battery", label:'${currentValue}% battery', unit:"%"
		}
        
        detailList << "battery"
        main "button"
        details(detailList)
    }
    
    preferences {

        section { // GENERAL:
            input (
                type: "paragraph",
                element: "paragraph",
                title: "Naming:",
                description: "Button Names (optional)."
            )

            input (
                name: "configB1name",
                title: "Name for button 1 (labelled with moon)",
                type: "string",
                defaultValue: "Button 1",
                required: false
            )

            input (
                name: "configB2name",
                title: "Name for button 2 (labelled with people)",
                type: "string",
                defaultValue: "Button 2",
                required: false
            )
            
            input (
                name: "configB3name",
                title: "Name for button 3 (labelled with circle)",
                type: "string",
                defaultValue: "Button 3",
                required: false
            )

            input (
                name: "configB4name",
                title: "Name for button 4 (labelled with power)",
                type: "string",
                defaultValue: "Button 4",
                required: false
            )
   		}
   }
}

def parse(String description) {
    //log.debug ("Parsing description:$description")
    def event
    def results = []
	
    //log.debug("RAW command: $description")
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


def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
        //log.debug ("SecurityMessageEncapsulation cmd:$cmd")
		//log.debug ("Secure command")
        def encapsulatedCommand = cmd.encapsulatedCommand([0x98: 1, 0x20: 1])

        if (encapsulatedCommand) {
            //log.debug ("SecurityMessageEncapsulation encapsulatedCommand:$encapsulatedCommand")
            return zwaveEvent(encapsulatedCommand)
        }
        log.debug ("No encalsulatedCommand Processed")
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    log.debug("Button Woke Up!")
    def event = createEvent(descriptionText: "${device.displayName} woke up", displayed: false)
    def cmds = []
    cmds += zwave.wakeUpV1.wakeUpNoMoreInformation()
    
    [event, encapSequence(cmds, 500)]
}

def zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    //log.debug( "CentralSceneNotification: $cmd")

	if (device.currentValue("lastSequence") != cmd.sequenceNumber){

		sendEvent(name: "lastSequence", value: cmd.sequenceNumber, displayed: false)
		buttonevent(cmd.sceneNumber, cmd.keyAttributes)

	} else {
		log.debug( "Duplicate sequenceNumber dropped!")
	}
}

def buttonevent(button, action) {

	def now = new Date().format("EEE dd/MM HH:mm:ss", location.timeZone)
	sendEvent(name: "lastPressedat", value: now, displayed: false)
    sendEvent(name: "lastPressed", value: "${state["button${button}name"]}", isStateChange: true, displayed: false)


	if (action == 0) {
			sendEvent(name: "button", value: "pushed", data: [buttonNumber: button], descriptionText: "$device.displayName button $button (${state["button${button}name"]}) was pushed", isStateChange: true)
            sendEvent(name: "button${button}status", value: "${state["button${button}name"]} pressed\n${now}", isStateChange: true, displayed: false)
            //sendEvent(name: "lastPressed", value: "${state["button${button}name"]} pressed", isStateChange: true, displayed: false)
		}
	else if (action == 2) {
			sendEvent(name: "button", value: "held", data: [buttonNumber: button], descriptionText: "$device.displayName button was $button held", isStateChange: true)
            sendEvent(name: "button${button}status", value: "${state["button${button}name"]} held\n${now}", isStateChange: true, displayed: false)
		}
	else if (action == 1) {
			sendEvent(name: "button", value: "released", data: [buttonNumber: button], descriptionText: "$device.displayName button was $button released", isStateChange: true)
            sendEvent(name: "button${button}status", value: "${state["button${button}name"]} released\n${now}", isStateChange: true, displayed: false)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
    log.debug("BatteryReport: $cmd")
	def val = (cmd.batteryLevel == 0xFF ? 1 : cmd.batteryLevel)
	if (val > 100) {
		val = 100
	}  	
	def isNew = (device.currentValue("battery") != val)    
	def result = []
	result << createEvent(name: "battery", value: val, unit: "%", display: isNew, isStateChange: isNew)	
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
    log.debug("V1 ConfigurationReport cmd: $cmd")
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
    log.debug("DeviceSpecificReport cmd: $cmd")
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    log.debug("ManufacturerSpecificReport cmd: $cmd")
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    // This will capture any commands not handled by other instances of zwaveEvent
    // and is recommended for development so you can see every command the device sends
    log.debug("Other unknown event: $cmd")
}

def buttonpush1() { buttonevent(1,0)}
def buttonpush2() { buttonevent(2,0)}
def buttonpush3() { buttonevent(3,0)}
def buttonpush4() { buttonevent(4,0)}

def installed() {
	initialize()
}

def initialize() {
	state.numberOfButtons = "4"
	sendEvent(name: "numberOfButtons", value: "4", displayed: false)
}

def updated() {
	//Run on save of settings
    log.debug("Saving settings")
    
	//Make sure still set to 4 buttons!
	def numberOfButtonsVal = device.currentValue("numberOfButtons")
    if ( !state.numberOfButtons || !numberOfButtonsVal || numberOfButtonsVal !=4) {
        log.debug ("Setting number of buttons to 4")
        state.numberOfButtons = "4"
        sendEvent(name: "numberOfButtons", value: "4", displayed: false)
    }
    
    (1..4).each{ n ->
			state."button${n}name" = settings."configB${n}name"
		}
}

private encapSequence(commands, delay=200) {
        delayBetween(commands.collect{ encap(it) }, delay)
}

private secure(physicalgraph.zwave.Command cmd) {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private nonsecure(physicalgraph.zwave.Command cmd) {
		"5601${cmd.format()}0000"
        log.debug("Insecure command sent cmd: $cmd")
}

private encap(physicalgraph.zwave.Command cmd) {

/*

Supported Command Classes
 
        Association Group Information V1 0x59
        Association V2					 0x85 X
        Battery V1						 0x80 X	
        Central Scene V2`				 0x5B X
        Configuration V1				 0x70 X
        Device Reset Local V1			 0x5A X
        Firmware Update MD V2			 0x7A 
        Manufacturer Specific V2		 0x72 X
        Powerlevel						 0x73
        Security V1						 0x98
        Version V2						 0x86 X
        Wake-Up V2						 0x84 X
        Z-Wave Plus Info V2
 
 
Controlled Command Classes
 
        Basic V1						 0x20
        Battery V1						 0x80
        Central Scene V2`				 0x5B
        Multilevel Switch V2			 0x26
*/

    def secureClasses = [0x5B, 0x85, 0x84, 0x5A, 0x86, 0x72, 0x70, 0x80, 0x59, 0x73, 0x26]
    if (secureClasses.find{ it == cmd.commandClassId }) {
        secure(cmd)
    } else {
        nonsecure(cmd)
    }
    

}