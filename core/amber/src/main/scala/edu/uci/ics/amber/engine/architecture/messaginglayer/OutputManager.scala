package edu.uci.ics.amber.engine.architecture.messaginglayer

import edu.uci.ics.amber.engine.architecture.messaginglayer.OutputManager.{
  getBatchSize,
  toPartitioner
}
import edu.uci.ics.amber.engine.architecture.sendsemantics.partitioners._
import edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings._
import edu.uci.ics.amber.engine.common.Constants
import edu.uci.ics.amber.engine.common.ambermessage.DataPayload
import edu.uci.ics.amber.engine.common.tuple.ITuple
import edu.uci.ics.amber.engine.common.virtualidentity.{ActorVirtualIdentity, LinkIdentity}

import scala.collection.mutable

object OutputManager {

  // create a corresponding partitioner for the given partitioning policy
  def toPartitioner(partitioning: Partitioning): Partitioner = {
    val partitioner = partitioning match {
      case oneToOnePartitioning: OneToOnePartitioning => OneToOnePartitioner(oneToOnePartitioning)
      case roundRobinPartitioning: RoundRobinPartitioning =>
        RoundRobinPartitioner(roundRobinPartitioning)
      case hashBasedShufflePartitioning: HashBasedShufflePartitioning =>
        HashBasedShufflePartitioner(hashBasedShufflePartitioning)
      case rangeBasedShufflePartitioning: RangeBasedShufflePartitioning =>
        RangeBasedShufflePartitioner(rangeBasedShufflePartitioning)
      case _ => throw new RuntimeException(s"partitioning $partitioning not supported")
    }

    // if reshape is enabled, wrap the original partitioner in a reshape partitioner
    if (Constants.reshapeSkewHandlingEnabled) {
      partitioner match {
        case p @ (_: RoundRobinPartitioner | _: HashBasedShufflePartitioner |
            _: RangeBasedShufflePartitioner) =>
          new ReshapePartitioner(p)
        case other => other
      }
    } else {
      partitioner
    }
  }

  def getBatchSize(partitioning: Partitioning): Int = {
    partitioning match {
      case p: OneToOnePartitioning          => p.batchSize
      case p: RoundRobinPartitioning        => p.batchSize
      case p: HashBasedShufflePartitioning  => p.batchSize
      case p: RangeBasedShufflePartitioning => p.batchSize
      case _                                => throw new RuntimeException(s"partitioning $partitioning not supported")
    }
  }
}

/** This class is a container of all the transfer partitioners.
  *
  * @param selfID         ActorVirtualIdentity of self.
  * @param dataOutputPort DataOutputPort
  */
class OutputManager(
    selfID: ActorVirtualIdentity,
    dataOutputPort: NetworkOutputPort[DataPayload]
) {

  val partitioners = mutable.HashMap[LinkIdentity, Partitioner]()

  val networkOutputBuffers =
    mutable.HashMap[(LinkIdentity, ActorVirtualIdentity), NetworkOutputBuffer]()

  /**
    * Add down stream operator and its corresponding Partitioner.
    * @param partitioning Partitioning, describes how and whom to send to.
    */
  def addPartitionerWithPartitioning(link: LinkIdentity, partitioning: Partitioning): Unit = {
    val partitioner = toPartitioner(partitioning)
    partitioners.update(link, partitioner)
    partitioner.allReceivers.foreach(receiver => {
      val buffer = new NetworkOutputBuffer(receiver, dataOutputPort, getBatchSize(partitioning))
      networkOutputBuffers.update((link, receiver), buffer)
    })
  }

  /**
    * Push one tuple to the downstream, will be batched by each transfer partitioning.
    * Should ONLY be called by DataProcessor.
    * @param tuple ITuple to be passed.
    */
  def passTupleToDownstream(
      tuple: ITuple,
      outputPort: LinkIdentity
  ): Unit = {
    val partitioner =
      partitioners.getOrElse(outputPort, throw new RuntimeException("output port not found"))
    val bucketIndex = partitioner.getBucketIndex(tuple)
    val destination = partitioner.allReceivers(bucketIndex)
    networkOutputBuffers((outputPort, destination)).addTuple(tuple)
  }

  /**
    * Send the last batch and EOU marker to all down streams
    */
  def emitEndOfUpstream(): Unit = {
    // flush all network buffers of this operator, emit end marker to network
    networkOutputBuffers.foreach(kv => {
      kv._2.flush()
      kv._2.noMore()
    })
  }

}