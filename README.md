# Spot 

_Spot's the differences_

Spot is a command line tool and library for scala that can be used to:
- create reports
- fail on difference
- be smart enough to recognize mutate on request fields (e.g time)
- handle multiple types of response (e.g xml, json, csv, plain text, html) with plugable extension libraries

## Planned
- [ ] fs2 / monix integration
- [ ] abstract above sttp eventually (for now just supplies http)

## Current Status (Naive)
- fails on difference

## Inspiration
(Diffy)[https://github.com/opendiffy/diffy] provides a great methodology for testing. However it's an external service and requires maintenance of it's own. Wouldn't it be great to expose API's to enable this done from the command line or even as part of an bespoke scala project? This is the approach spot aims to take! By providing high level API's for consuming and reporting differences.

## Long term goals
Spot is different in it's approach but aims to allow users to define their own way to measure confidence and feed that number of live requests back into diffy to measure the difference when considering a candidate release. This methodology should hopefully reduce the need for QA to spend on regression and allow them to spend time looking at more sinister and hidden bugs.

read (this white paper)[http://www.xmailserver.org/diff2.pdf] to implement better diffing algorithms


### Planned API
```scala 
implicit val jsonComparator: Comparator[Resp, Json] = {
  def compare(x: Resp, y: Resp): Comparison[Json] = {
    val (xJson, yJson): (Json, Json) = (x.as[Json], y.as[Json])
    DifferenceValidatorNec.compare(xJson, yJson)
  }
}
val requests = new Sink((x,y) => Comparator.compare(x,y)) # find a way to implement this
or
val requests: Bucket[List[_], (Resp, Resp) => Comparison] = new Bucket(List(json, binary, xml), (x, y) => c.compare(x, y))

val liveHostNames: List[Host] = List(Host("https", "pe-04.lhr"), Host("https", "pe-05.ams"))
val stageHostNames: List[Host] = List(Host("https", "pe-stage-05.ams"))
requests.candidates(stageHostNames).productions(liveHostNames).compare
```
