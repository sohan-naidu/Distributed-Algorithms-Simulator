package enricher

import core.{Message, Distribution}

object PDFSampler:
  private def normalize(weights: Map[Message, Double]): Map[Message, Double] =
    val total = weights.values.sum
    weights.view.mapValues(_ / total).toMap

  def uniform(messages: Seq[Message], seed: Long): Map[Message, Double] =
    val rng = new scala.util.Random(seed)
    val rawWeights = messages.map(m => m -> (rng.nextDouble() + 0.1)).toMap
    normalize(rawWeights)

  def zipf(messages: Seq[Message], exponent: Double, seed: Long): Map[Message, Double] =
    // Zipf: weight of rank k is 1/k^exponent. Shuffle first with the seed
    // so the rank ordering is not always alphabetical.
    val rng = new scala.util.Random(seed)
    val shuffled = rng.shuffle(messages.toList)
    val rawWeights = shuffled.zipWithIndex.map { (msg, idx) =>
      val rank = idx + 1
      msg -> (1.0 / math.pow(rank, exponent))
    }.toMap
    normalize(rawWeights)

  def fromConfig(dist: Distribution, seed: Long): Map[Message, Double] =
    dist match
      case Distribution.Uniform       => uniform(Message.values, seed)
      case Distribution.Zipf(exp)     => zipf(Message.values, exp, seed)