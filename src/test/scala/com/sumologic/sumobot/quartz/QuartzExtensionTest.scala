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
package com.sumologic.sumobot.quartz

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import com.sumologic.sumobot.test.SumoBotSpec
import com.sumologic.sumobot.test.annotated.SumoBotTestKit
import org.quartz.CronExpression

import scala.concurrent.duration._

class QuartzExtensionTest
  extends SumoBotTestKit(ActorSystem("QuartzExtensionTest")) {

  object TestMessage

  "QuartzExtension" should {
    "allow scheduling jobs using cron" in {
      val quartz = QuartzExtension(system)
      val probe = TestProbe()

      new CronExpression("0 0 8,12,20 ? * MON-FRI")

      // This expression should trigger every second.
      quartz.scheduleMessage("test", "* * * * * ?", probe.ref, TestMessage)
      probe.expectMsg(5.seconds, TestMessage)
    }
  }
}
