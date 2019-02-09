/**
 *  Metering Switch Child Device
 *
 *  Copyright 2019 Andrew Waghorn
 *
 *  Based on Metering Switch Child Device
 *
 *  Copyright 2017 Eric Maycock
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
	definition (name: "Metering Switch Child Device", namespace: "awaghorn", author: "Andrew Waghorn") {
		capability "Switch"
		capability "Actuator"
		capability "Sensor"
        capability "Energy Meter"
        capability "Power Meter"
        capability "Refresh"
        
        attribute "lastphysoff", "string" //time (epoch) last physically switched off
        attribute "lastphyslag", "number" //lag to apply before allowing non-physical reactivation
        attribute "lastphysoption", "boolean" //whether to apply last physical timing logic
        
        command "reset"
	}

	tiles {
		multiAttributeTile(name:"switch", type: "lighting", width: 3, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState:"turningOn"
				attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC", nextState:"turningOff"
				attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
			}
		}
        valueTile("power", "device.power", decoration: "flat", width: 2, height: 2) {
			state "default", label:'${currentValue} W'
	    }
        valueTile("energy", "device.energy", decoration: "flat", width: 2, height: 2) {
			state "default", label:'${currentValue} kWh'
	    }
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
		    state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
		    state "default", label:'reset kWh', action:"reset"
	    }
	}
}

void on() {
	parent.childOn(device.deviceNetworkId)
}

void off() {
	parent.childOff(device.deviceNetworkId)
}

void refresh() {
	parent.childRefresh(device.deviceNetworkId)
}

void reset() {
	parent.childReset(device.deviceNetworkId)
}

//Added by Andrew Waghorn

void configPhysOff(configlastphyslag, configlastphysoption) {
	//Set by update/install tasks in parent
    parent.logging("setting configPhysOff with inputs ${configlastphyslag} and ${configlastphysoption}")
    state.lastphyslag = configlastphyslag.toInteger() * 1000 // multiple by 1000 to convert to milliseconds
    state.lastphysoption = configlastphysoption
}

void setlastphysoff(i_lastphysoff) {
	state.lastphysoff = i_lastphysoff
}

//Workaround per ST documentation at: https://docs.smartthings.com/en/latest/smartapp-developers-guide/state.html
def getStateValue(key) {
    return state[key]
}