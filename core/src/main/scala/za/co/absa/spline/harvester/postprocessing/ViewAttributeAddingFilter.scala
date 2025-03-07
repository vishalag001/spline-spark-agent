/*
 * Copyright 2022 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.spline.harvester.postprocessing

import za.co.absa.spline.harvester.HarvestingContext
import za.co.absa.spline.producer.model.{AttrOrExprRef, DataOperation, ExecutionPlan}

class ViewAttributeAddingFilter extends AbstractInternalPostProcessingFilter {

  override def processExecutionPlan(plan: ExecutionPlan, ctx: HarvestingContext): ExecutionPlan = {
    val views = plan.operations.other
      .getOrElse(Seq.empty)
      .filter(_.name.get.startsWith("View"))


    if (views.isEmpty)
      plan
    else
      addMissingAttributeLinks(plan, views)
  }

  private def addMissingAttributeLinks(plan: ExecutionPlan, views: Seq[DataOperation]): ExecutionPlan = {
    val attributeDependencies = views
      .map(toAttributeReferencesMap(plan, _))
      .reduce(_ ++ _)

    plan.copy(
      attributes = plan.attributes.map { attSeq =>
        attSeq.map { att =>
          val maybeAttrId = attributeDependencies.get(att.id)
          if (maybeAttrId.nonEmpty) {
            att.copy(childRefs = Some(Seq(AttrOrExprRef(maybeAttrId, None))))
          } else {
            att
          }
        }
      }
    )
  }

  private def toAttributeReferencesMap(plan: ExecutionPlan, view: DataOperation): Map[String, String] = {
    // assume views can have only one child in Spark
    val childId = view.childIds.get.head
    val child = plan.operations.other.getOrElse(Seq.empty).find(_.id == childId).get

    val viewOutput = view.output.get
    val childOutput = child.output.get

    if (viewOutput.size != childOutput.size) {
      throw new UnsupportedOperationException("Sizes of outputs of view operation and it's child are different!")
    }

    viewOutput.zip(childOutput)
      .filter { case (v, ch) => v != ch }
      .toMap
  }
}
