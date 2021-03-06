/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.api.scala.batch.table.stringexpr

import org.apache.flink.api.scala._
import org.apache.flink.api.scala.util.CollectionDataSets
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.api.scala._
import org.apache.flink.table.utils.TableTestBase
import org.apache.flink.table.api.java.utils.UserDefinedAggFunctions.WeightedAvgWithMergeAndReset
import org.apache.flink.table.api.scala.batch.utils.LogicalPlanFormatUtils
import org.apache.flink.table.functions.aggfunctions.CountAggFunction
import org.junit._

class AggregationsStringExpressionTest extends TableTestBase {

  @Test
  def testAggregationTypes(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = CollectionDataSets.get3TupleDataSet(env).toTable(tEnv)

    val t1 = t.select('_1.sum, '_1.sum0, '_1.min, '_1.max, '_1.count, '_1.avg)
    val t2 = t.select("_1.sum, _1.sum0, _1.min, _1.max, _1.count, _1.avg")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testWorkingAggregationDataTypes(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = env.fromElements(
      (1: Byte, 1: Short, 1, 1L, 1.0f, 1.0d, "Hello"),
      (2: Byte, 2: Short, 2, 2L, 2.0f, 2.0d, "Ciao")).toTable(tEnv)

    val t1 = t.select('_1.avg, '_2.avg, '_3.avg, '_4.avg, '_5.avg, '_6.avg, '_7.count)
    val t2 = t.select("_1.avg, _2.avg, _3.avg, _4.avg, _5.avg, _6.avg, _7.count")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testProjection(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = env.fromElements(
      (1: Byte, 1: Short),
      (2: Byte, 2: Short)).toTable(tEnv)

    val t1 = t.select('_1.avg, '_1.sum, '_1.count, '_2.avg, '_2.sum)
    val t2 = t.select("_1.avg, _1.sum, _1.count, _2.avg, _2.sum")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testAggregationWithArithmetic(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = env.fromElements((1f, "Hello"), (2f, "Ciao")).toTable(tEnv)

    val t1 = t.select(('_1 + 2).avg + 2, '_2.count + 5)
    val t2 = t.select("(_1 + 2).avg + 2, _2.count + 5")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testAggregationWithTwoCount(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = env.fromElements((1f, "Hello"), (2f, "Ciao")).toTable(tEnv)

    val t1 = t.select('_1.count, '_2.count)
    val t2 = t.select("_1.count, _2.count")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testAggregationAfterProjection(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = env.fromElements(
      (1: Byte, 1: Short, 1, 1L, 1.0f, 1.0d, "Hello"),
      (2: Byte, 2: Short, 2, 2L, 2.0f, 2.0d, "Ciao")).toTable(tEnv)

    val t1 = t.select('_1, '_2, '_3)
      .select('_1.avg, '_2.sum, '_3.count)

    val t2 = t.select("_1, _2, _3")
      .select("_1.avg, _2.sum, _3.count")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testDistinct(): Unit = {
    val env: ExecutionEnvironment = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val ds = CollectionDataSets.get3TupleDataSet(env).toTable(tEnv, 'a, 'b, 'c)

    val distinct = ds.select('b).distinct()
    val distinct2 = ds.select("b").distinct()

    verifyTableEquals(distinct, distinct2)
  }

  @Test
  def testDistinctAfterAggregate(): Unit = {
    val env: ExecutionEnvironment = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val ds = CollectionDataSets.get5TupleDataSet(env).toTable(tEnv, 'a, 'b, 'c, 'd, 'e)

    val distinct = ds.groupBy('a, 'e).select('e).distinct()
    val distinct2 = ds.groupBy("a, e").select("e").distinct()

    val lPlan1 = distinct.logicalPlan
    val lPlan2 = distinct2.logicalPlan

    Assert.assertEquals("Logical Plans do not match", lPlan1, lPlan2)
  }

  @Test
  def testGroupedAggregate(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = CollectionDataSets.get3TupleDataSet(env).toTable(tEnv, 'a, 'b, 'c)

    val t1 = t.groupBy('b).select('b, 'a.sum)
    val t2 = t.groupBy("b").select("b, a.sum")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testGroupingKeyForwardIfNotUsed(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = CollectionDataSets.get3TupleDataSet(env).toTable(tEnv, 'a, 'b, 'c)

    val t1 = t.groupBy('b).select('a.sum)
    val t2 = t.groupBy("b").select("a.sum")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testGroupNoAggregation(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = CollectionDataSets.get3TupleDataSet(env).toTable(tEnv, 'a, 'b, 'c)

    val t1 = t
      .groupBy('b)
      .select('a.sum as 'd, 'b)
      .groupBy('b, 'd)
      .select('b)

    val t2 = t
      .groupBy("b")
      .select("a.sum as d, b")
      .groupBy("b, d")
      .select("b")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testGroupedAggregateWithConstant1(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = CollectionDataSets.get3TupleDataSet(env).toTable(tEnv, 'a, 'b, 'c)

    val t1 = t.select('a, 4 as 'four, 'b)
      .groupBy('four, 'a)
      .select('four, 'b.sum)

    val t2 = t.select("a, 4 as four, b")
      .groupBy("four, a")
      .select("four, b.sum")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testGroupedAggregateWithConstant2(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = CollectionDataSets.get3TupleDataSet(env).toTable(tEnv, 'a, 'b, 'c)

    val t1 = t.select('b, 4 as 'four, 'a)
      .groupBy('b, 'four)
      .select('four, 'a.sum)
    val t2 = t.select("b, 4 as four, a")
      .groupBy("b, four")
      .select("four, a.sum")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testGroupedAggregateWithExpression(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = CollectionDataSets.get5TupleDataSet(env).toTable(tEnv, 'a, 'b, 'c, 'd, 'e)

    val t1 = t.groupBy('e, 'b % 3)
      .select('c.min, 'e, 'a.avg, 'd.count)
    val t2 = t.groupBy("e, b % 3")
      .select("c.min, e, a.avg, d.count")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testGroupedAggregateWithFilter(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = CollectionDataSets.get3TupleDataSet(env).toTable(tEnv, 'a, 'b, 'c)

    val t1 = t.groupBy('b)
      .select('b, 'a.sum)
      .where('b === 2)
    val t2 = t.groupBy("b")
      .select("b, a.sum")
      .where("b = 2")

    verifyTableEquals(t1, t2)
  }

  @Test
  def testAnalyticAggregation(): Unit = {
    val util = batchTestUtil()
    val t = util.addTable[(Int, Long, Float, Double)]('_1, '_2, '_3, '_4)

    val resScala = t.select(
      '_1.stddevPop, '_2.stddevPop, '_3.stddevPop, '_4.stddevPop,
      '_1.stddevSamp, '_2.stddevSamp, '_3.stddevSamp, '_4.stddevSamp,
      '_1.varPop, '_2.varPop, '_3.varPop, '_4.varPop,
      '_1.varSamp, '_2.varSamp, '_3.varSamp, '_4.varSamp)
    val resJava = t.select("""
      _1.stddevPop, _2.stddevPop, _3.stddevPop, _4.stddevPop,
      _1.stddevSamp, _2.stddevSamp, _3.stddevSamp, _4.stddevSamp,
      _1.varPop, _2.varPop, _3.varPop, _4.varPop,
      _1.varSamp, _2.varSamp, _3.varSamp, _4.varSamp""")

    verifyTableEquals(resScala, resJava)
  }

  @Test
  def testAggregateWithUDAGG(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = CollectionDataSets.get3TupleDataSet(env).toTable(tEnv, 'a, 'b, 'c)

    val myCnt = new CountAggFunction
    tEnv.registerFunction("myCnt", myCnt)
    val myWeightedAvg = new WeightedAvgWithMergeAndReset
    tEnv.registerFunction("myWeightedAvg", myWeightedAvg)

    val t1 = t.select(myCnt('a) as 'aCnt, myWeightedAvg('b, 'a) as 'wAvg)
    val t2 = t.select("myCnt(a) as aCnt, myWeightedAvg(b, a) as wAvg")

    val lPlan1 = t1.logicalPlan
    val lPlan2 = t2.logicalPlan

    val x = LogicalPlanFormatUtils.formatTempTableId(lPlan1.toString)
    val y = LogicalPlanFormatUtils.formatTempTableId(lPlan2.toString)

    Assert.assertEquals(
      "Logical Plans do not match",
      LogicalPlanFormatUtils.formatTempTableId(lPlan1.toString),
      LogicalPlanFormatUtils.formatTempTableId(lPlan2.toString))
  }

  @Test
  def testGroupedAggregateWithUDAGG(): Unit = {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    val t = CollectionDataSets.get3TupleDataSet(env).toTable(tEnv, 'a, 'b, 'c)

    val myCnt = new CountAggFunction
    tEnv.registerFunction("myCnt", myCnt)
    val myWeightedAvg = new WeightedAvgWithMergeAndReset
    tEnv.registerFunction("myWeightedAvg", myWeightedAvg)

    val t1 = t.groupBy('b)
      .select('b, myCnt('a) + 9 as 'aCnt, myWeightedAvg('b, 'a) * 2 as 'wAvg, myWeightedAvg('a, 'a))
    val t2 = t.groupBy("b")
      .select("b, myCnt(a) + 9 as aCnt, myWeightedAvg(b, a) * 2 as wAvg, myWeightedAvg(a, a)")

    val lPlan1 = t1.logicalPlan
    val lPlan2 = t2.logicalPlan

    Assert.assertEquals(
      "Logical Plans do not match",
      LogicalPlanFormatUtils.formatTempTableId(lPlan1.toString),
      LogicalPlanFormatUtils.formatTempTableId(lPlan2.toString))
  }

}
