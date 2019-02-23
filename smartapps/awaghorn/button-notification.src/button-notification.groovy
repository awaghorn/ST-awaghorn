/**
 *  Created by Andrew Waghorn, based on Notify Me When from ST
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
 *
 */
definition(
		name: "Button Notification",
		namespace: "awaghorn",
		author: "Andrew Waghorn",
		description: "Receive notification when button pressed.",
		category: "Convenience",
		iconUrl: "https://s3.amazonaws.com/smartapp-icons/Categories/damageAndDanger.png",
		iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Categories/damageAndDanger@2x.png",
		pausable: true
)

preferences {
	page(name: "prefsPage")
/*    page (name: "pref2event", title: "Select Button Event", uninstall: false) {
        section("Choose button event to trigger notification"){
            input "buttonEvent", "enum", title: "Button Event", required: true, multiple: false
        }
    }*/
}

def prefsPage() {
	dynamicPage (name: "prefsPage", title: "Main Configuration", install: true, uninstall: true) {
        section("Choose button to trigger..."){
            input "button", "capability.button", title: "Button Pushed", required: true, multiple: false, submitOnChange: true
        }
        
        if (button) {
			section("Button Event") {
        		input("buttonEvent", "enum", title: "Select Button Event to trigger message", required: false, multiple: false, options: prefEvents(button))
	        }
        }
        section("Send this message (optional, sends standard status message if not specified)"){
            input "messageText", "text", title: "Message Text", required: false
        }
        section("Via a push notification and/or an SMS message (SMS sent if number defined)"){
            input("recipients", "contact", title: "Send notifications to") {
                input "pushAndPhone", "enum", title: "Notify me via Push Notification", required: false, options: ["Yes", "No"]
                input "phone1", "phone", title: "Enter first phone number to get SMS", required: false
                input "phone2", "phone", title: "Enter second phone number to get SMS", required: false
                input "phone3", "phone", title: "Enter third phone number to get SMS", required: false
                input "phone4", "phone", title: "Enter fourth phone number to get SMS", required: false
                paragraph "If outside the US please make sure to enter the proper country code"
            }
        }
        section("Minimum time between push messages (optional, defaults to every message)") {
            input "frequency", "number", title: "Minutes", required: false
        }
        section("Minimum time between SMS messages (optional, defaults to 15 minutes)") {
            input "frequencySMS", "number", title: "Minutes", required: true, defaultValue: "15"; range:"15..*" 
        }
        section("Name app and configure modes") {
            label(title: "Assign a name", required: true)
            mode(title: "Run only for specific mode(s)", required: false)

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
	//log.debug("testing sub build: $button.$buttonEvent ")
	subscribe(button, "$buttonEvent", eventHandler)
}

def eventHandler(evt) {
	log.debug "Notify got evt ${evt}"
   	log.debug "$evt.name:$evt.value, pushAndPhone:$pushAndPhone, '$msg'"

	if (frequency) {
		def lastTime = state["${evt.deviceId}-push"]
		if (lastTime == null || now() - lastTime >= frequency * 60000) {
			sendMessagePush(evt)
		}
	}
	else {
		sendMessagePush(evt)
	}
    if (phone1 || phone2 || phone3 || phone4) {
		def lastTimeSMS = state["${evt.deviceId}-SMS"]
		if (lastTimeSMS == null || now() - lastTimeSMS >= frequencySMS * 60000) {
			sendMessageSMS(evt)
		} else {
        	log.info("No SMS sent due to rate limiting")
        }
    }
}

private sendMessagePush(evt) {
	String msg = prepMessageText(evt)

	if (location.contactBookEnabled) {
		sendNotificationToContacts(msg, recipients, options)
	} else if (pushAndPhone == 'Yes') {
    	sendPush(msg)
    } else {
    	// Do nothing
        log.info("No push message sent due to settings")
    }

	if (frequency) {
		state["${evt.deviceId}-push"] = now()
	}
}

private sendMessageSMS(evt) {
	String msg = prepMessageText(evt)
	Integer msglen = msg.length() < 140 ? msg.length() : 140
    msg = msg.substring(0,msglen)

	if (location.contactBookEnabled) {
		sendNotificationToContacts(msg, recipients, options)
	} else {
    	(1..4).each{ p->
        	if (settings["phone$p"])
        	sendSmsMessage(settings["phone$p"],msg)
        }
    }

	state["${evt.deviceId}-SMS"] = now()
}


private prepMessageText(event) {
	String msg
	if (!messageText) {
		msg = "${evt.displayName} sent ${evt.descriptionText}"
        log.debug("Setting message using default rules to $msg")
	} else {
    	msg = messageText
    }
    return msg
}