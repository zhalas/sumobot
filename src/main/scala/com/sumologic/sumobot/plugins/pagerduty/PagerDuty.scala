/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sumologic.sumobot.plugins.pagerduty

import akka.actor.ActorLogging
import com.google.common.annotations.VisibleForTesting
import com.sumologic.sumobot.Receptionist.{BotMessage, SendSlackMessage}
import com.sumologic.sumobot.plugins.BotPlugin
import slack.rtm.RtmState

import scala.concurrent.ExecutionContext.Implicits.global

trait EscalationPolicyFilter {
  def filter(botMessage: BotMessage, policies: Seq[PagerDutyEscalationPolicy])(implicit state: RtmState): Seq[PagerDutyEscalationPolicy]
}

/**
 * @author Chris (chris@sumologic.com)
 */
class PagerDuty(manager: PagerDutySchedulesManager,
                policyFilter: Option[EscalationPolicyFilter]) extends BotPlugin with ActorLogging {

  override protected def name: String = "pagerduty"

  override protected def help: String =
    """
      |Communicate with PagerDuty to learn about on-call processes. And stuff.
      |
      |who's on call? - I'll tell you!
    """.stripMargin

  // TODO: Turn these into actual settings
  val maximumLevel = 2
  val ignoreTest = true // Ignore policies containing the word test

  @VisibleForTesting protected[pagerduty] val WhosOnCall = matchText("who'?s on\\s?call(?: for (.+?))?\\??")

  override protected def receiveBotMessage: ReceiveBotMessage = {
    case botMessage@BotMessage(WhosOnCall(filter), _, _, _) =>
      botMessage.respondInFuture(whoIsOnCall(_, maximumLevel, Option(filter)))
  }

  private[this] def whoIsOnCall(msg: BotMessage,
                                maximumLevel: Int,
                                filterOpt: Option[String]): SendSlackMessage = {
    manager.getEscalationPolicies match {
      case Some(policies) =>
        val escalationPolicies = policies.escalation_policies
        val nonTestPolicies = escalationPolicies.filter {
          policy => !(ignoreTest && policy.name.toLowerCase.contains("test"))
        }

        // TODO: Teach the filter to be smarter about how it handles stuff since this text matching is stupidly simple
        val partiallyFilteredPolicies = nonTestPolicies.filter {
          policy =>
            (filterOpt.isEmpty ||
              filterOpt.exists(filter => policy.name.toLowerCase.contains(filter.toLowerCase))) &&
              policy.on_call.nonEmpty
        }

        val nonFilteredPolicies = policyFilter match {
          case Some(filter) => filter.filter(msg, partiallyFilteredPolicies)(state)
          case None => partiallyFilteredPolicies
        }

        if (nonFilteredPolicies.isEmpty) {
          msg.response("No escalation policies matched your filter.")
        } else {
          val outputString = nonFilteredPolicies.map {
            policy =>
              val onCalls = policy.on_call.filter(_.level <= maximumLevel).groupBy(_.level).toList.sortBy(_._1).map {
                tpl =>
                  val level = tpl._1
                  val oncalls = tpl._2.map(_.user.name).sorted.mkString(", ")
                  val levelName = level match {
                    case 1 => "primary"
                    case 2 => "secondary"
                    case 3 => "tertiary"
                    case other => s"level $other"
                  }
                  s"- _$levelName:_ $oncalls"
              }.mkString("\n", "\n", "\n")

              "*" + policy.name + "*" + onCalls
          }.mkString("\n")

          msg.message(outputString)
        }

      case None =>
        msg.response("Unable to login or something.")
    }
  }
}
