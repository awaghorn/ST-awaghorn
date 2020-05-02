/**
 *  Have You Remembered To
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
definition(
    name: "Have You Remembered To",
    namespace: "awaghorn",
    author: "Andrew Waghorn",
    description: "App to notify you if a sensor has not been activated by a certain time",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	page(name: "prefsPage")
}

def prefsPage() {
	dynamicPage (name: "prefsPage", title: "Main Configuration", install: true, uninstall: true) {
          
        section("Basic Settings"){
            input "startTime", "time", title: "Start time to monitor from", required: true
            input "cutOffTime", "time", title: "Cut-off time to be notified", required: true
            input "cutOffDays", "enum", title: "Days of week to check", required: false, options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true
            input "triggerContact", "capability.contactSensor", title: "Open / Close Sensor that indicates event has happened", required: false, multiple: false
            input "triggerMotion", "capability.motionSensor", title: "Motion Sensor that indicates event has happened", required: false, multiple: false
        	paragraph "Make sure at least one contact OR motion sensor is selected"        
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

def installed() {
	log.debug "Installed with settings: ${settings}"
	//subscribeToEvents()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	//subscribeToEvents()
    setupSchedule()
}

def subscribeToEvents() {
	//log.debug("testing sub build: $button.$buttonEvent ")
	//subscribe(button, "$buttonEvent", eventHandler)
}

def setupSchedule() {
	
    log.trace("$startTime")
    
	def sHH = timeToday(cutOffTime,location.timeZone).format("H")
    def sMM = timeToday(cutOffTime,location.timeZone).format("m")
    
    String shortday
	
    def sDay = ""
    if (cutOffDays) {
    	cutOffDays.each {d->
        	shortday = d.substring(0,3)
        	sDay = sDay + ",$shortday"
        }
        sDay = sDay.substring(1,sDay.length())
    } else {
    	sDay = "*"
    }
    
    log.debug("Hours are: $sHH and minutes are: $sMM and days are: $sDay so finish schedule is 23 $sMM $sHH ? * $sDay")
    schedule("23 $sMM $sHH ? * $sDay", watchEnd)

}

def watchEnd() {
	def triggerEvents = []
    triggerEvents.addAll(triggerContact.eventsBetween(timeToday(startTime,location.timeZone),timeToday(cutOffTime,location.timeZone)))
    triggerEvents.addAll(triggerMotion.eventsBetween(timeToday(startTime,location.timeZone),timeToday(cutOffTime,location.timeZone)))
    log.debug("$triggerEvents")
    if (!triggerEvents) { //no  events therefore send message
    	log.debug("send event here")
        def evt = [name: "trigger", value: "no activity found, sending message", descriptionText: "Trigger Event"]
        log.debug("$evt")
        messageEvent(evt)
    }

}

def messageEvent(evt) {
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


private prepMessageText(evt) {
	String msg
	if (!messageText) {
		msg = "${evt.displayName} sent ${evt.descriptionText}"
        log.debug("Setting message using default rules to $msg")
	} else {
    	msg = messageText
    }
    return msg
}