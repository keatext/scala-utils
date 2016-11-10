scala-utils
===

The codebase of [keatext.ai](https://app.keatext.ai) is divided into multiple microservices. Since each microservice lives in a separate codebase, the only way to share code is to put it in a separate library. We have two such libraries: an internal one for the pieces which are specific to keatext.ai, and this public one for the pieces which are generic enough that they could benefit any Scala project.


Downsides
---

At the moment, all the pieces are piled up in a single heterogeneous library. As we accumulate more, it might make more sense to split them into several smaller, specialized libraries. Another good reason for doing so is that since some of the pieces use akka-http, Slick, and spray-json, any microservice using any piece from `scala-utils` will now have to depend on all three dependencies, even if that microservice chose a completely different way to define their routes, their database tables, or their JSON conversions. Worse, even if that microservice does use all three, depending on `scala-utils` means that they now have to use the same version of those three dependencies as `scala-utils` does.

This makes it more difficult to migrate to a more recent version of either of those dependencies. Ideally, we would like to perform such upgrades gradually, one microservice at a time. But if we do that, there is a period of time during which different microservices will use different versions of `scala-utils`, which means one of them is using an older version. In turn, this means that whenever we write reusable code in that microservice, we won't be able to move it to `scala-utils`, and this might in turn lead to code duplication.

I must also admit that adding code to `scala-utils` is more annoying than adding it directly to a module of the current microservice, and as a result, we currently have a lot of reusable code which lives in individual microservices. I think it's okay for it to stay there for now, until we happen to need it in a different module: performing the refactoring is annoying, but not nearly as annoying as maintaining two separate copies of the same code.

If you do want to add your reusable code directly to `scala-utils`, run `sbt '~ publish-local'` so that sbt continuously publishes your latest modifications to `scala-utils` to your local Ivy repository, and in your microservice, set your `scala-utils` version to the `X.Y.Z-SNAPSHOT` version listed in `version.sbt`. Then, once you are ready to commit, push your changes to `scala-utils` first so that Bamboo publishes a non-snapshot version `X.Y.Z`, and change your microservice to use that version of `scala-utils`. You must not commit a `build.sbt` which uses a `-SNAPSHOT` version, because on other machines, `sbt compile` will complain that it cannot find this version.


Reusable pieces
---

`DatabaseManager` makes it easy to conditionally initialize a postgres database using Slick. Instantiate a concrete `MyDatabaseManager` singleton for your project, instantiating `tables` to the concrete list of all the Slick classes which represent your tables, and call `createTablesIfNeeded` at the start of your program: the tables will be created unless they already exist.

This doesn't just check that tables with the correct name exist, it also makes sure that those existing tables have the expected schema. This way, if you update the schema of your tables and later run a version of your microservice which expects the older version, you'll get an exception on startup instead of later on when the table is needed. This is even more useful when you did upgrade the schema of your local and staging databases and you do run the latest version of the code which expected this new schema, but you forgot to upgrade the schema in prod.

One bit of related code which should probably be made more reusable and moved here is the migration logic in `mintosci-subscriptions`. Instead of expecting the table schema to be updated at deployment time, that version creates a separate table and migrates all the existing data to it; this way, the older version of the code can continue to use the older version of the table. I'm not sure what to do about the events which come in after that migration is performed but before the event source is switched from the older version of the code to the newer version of the code: the older version of the table will be modified accordingly, but then those changes will be lost after the switch. This kind of corner cases is one of the reasons I have been pushing for a datomic-style database in which we can work on a snapshot from a particular time and then replay all the events starting from that time.

----

`FutureTraverse` was originally added for the sole purpose of adding the method [`Future.traverse`](http://www.scala-lang.org/api/current/scala/concurrent/Future$.html#traverse[A,B,M[X]<:TraversableOnce[X]](in:M[A])(fn:A=>scala.concurrent.Future[B])(implicitcbf:scala.collection.generic.CanBuildFrom[M[A],B,M[B]],implicitexecutor:scala.concurrent.ExecutionContext):scala.concurrent.Future[M[B]]), which didn't exist at the time. Instead, there was `Future.sequence`, which executes a bunch of Futures in parallel.

It is easy to `map` a function `A => Future[A]` onto a `List[A]` to obtain a `List[Future[A]]`, and then to `sequence` that list into a `Future[List[A]]`. But then all of those functions will execute in parallel, and that exhausted all of the database handles we had and led to a failure. We were looking for a version of `Future.sequence` which would execute its futures, well, sequentially.

Unfortunately such a version cannot exist, because once it receives its `List[Future[A]]`, all of those futures are already running and it is too late to stop them. So instead, we want to delay the creation of the futures, using something like a `List[() => Future[A]]`. Taking inspiration from Haskell's `traverse`, I chose the following nicer API instead:

```scala
def traverse(inputs: List[A])(f: A => Future[B]): Future[List[B]]
```

The actual type is a bit scarier than that in order to also support sequences other than `List`, but it's much simpler to think of it as having the above type.

Today, Scala's standard library does have a `Future.traverse` method, but it uses `map` and `sequence` to run all of those functions in parallel. Since that's still not what we want to do, `FutureTraverse.traverse` is still useful, but it would probably be a good idea to give it a better name.

----

`FutureTraverse.filter` is a variant of `FutureTraverse.traverse` in which the function is used to filter the input list instead of transforming its elements. We should keep adding such helper methods here whenever we encounter a situation in which we want to execute a function sequentially but the standard `Future` either executes it in parallel or doesn't support it.

----

`FutureTraverse.fromBlocking` is a simple wrapper for the idiom `Future { blocking {...} }`. The `blocking` annotation is important (or so I have read on the internet), it tells the `ExecutionContext` that this thread is going to be blocked on some synchronous call. Given this, it is unfortunate that the `Future {...}` constructor exists at all: given a computation, it is either a slow operation which needs both `Future` and `blocking`, or it is a fast computation like `x + y` which isn't worth executing in a different thread and `Future.successful(x + y)` should be used instead of `Future {x + y}`.

We cannot ban `Future {...}` from the language, but we can train ourselves to see it as a code smell, and to insist on always using either `Future.successful` or `Future.fromBlocking` instead.

There is no link between `FutureTraverse.fromBlocking` and `FutureTraverse.traverse`, they are only defined in the same singleton object because they are both related to Futures. The singleton object should probably be renamed to `FutureUtils` or something.

----

`FutureTry.sequence` is a version of `Future.sequence` which runs N long-running and possibly-failing computations in parallel and returns the failures and successes via N values of type `Try[A]`.

The normal `Future.sequence` also runs N long-running and possibly-failing computations in parallel, but if any of the computations fail, the entire sequence is deemed to have failed. Note that despite the name, acheiving the result of `FutureTry.sequence` is not as easy as running each of those computations inside a `Try` block. If you put the `Try` block outside of the `Future` block, the creation of the `Future` block will succeed even the computation it describes fails at runtime, and if you put the `Try` block inside of the `Future` block, you won't be able to run any `Future` computation inside of it.

Note that there is no benefit to adding `map` and `flatMap` to `FutureTry` in order to benefit from a `for..yield` syntax for it. If we did, we would have to convert many of the `Future` sub-computations into `FutureTry` sub-computations, like this:

```scala
val computeX: Future[Int]
val computeY: Future[Int]
val futureTry: FutureTry[Int] =
  for {
    x <- FutureTry(computeX)
    y <- FutureTry(computeY)
  } yield x + y
```

Whereas without a `for..yield` notation for `FutureTry`, the user is naturally pushed towards a shorter syntax in which a single `FutureTry` wrapper is used:

```scala
val futureTry: FutureTry[Int] =
  FutureTry {
    for {
      x <- computeX
      y <- computeY
    } yield x + y
  }
```

This works because `Future` already keeps track of the exceptions raised within its computation, we simply expose it with a more convenient interface based on `Try`.

----

`HttpRequests` is a wrapper around akka-http's `Http().singleRequest` method for sending HTTP requests. I wrote this wrapper because I wrote two microservices which constantly need to make authenticated HTTP requests in order to talk to external services like Stripe or Zendesk. So I wrote `StripeRequests` and `ZendeskRequests` to automatically add the authentication boilerplate to every call, and I refactored the common part into `HttpRequests`. It also automatically waits and retries if we use the API too much and receive a 429 error, this was required for Zendesk and might be useful for other external services as well, assuming the error code and headers used are standard.

----

`JsonColumn` is a helper for writing Slick table descriptions. It's much easier to write JSON converters using spray-json than to write table descriptions using Slick, so for those sets of columns which are never used to perform lookups, it's easier to just convert the data to JSON and to store it in a string column. Note that postgres supports a JSON column type, but then the column type is literally JSON, which means it can hold anything. Here, Slick makes sure that we only put in values of type `A`, and the fact that it gets serialized to JSON is a technical detail.

This isn't used by any of our microservices anymore, but it seems likely that we might want to use it in the future, and having this short class lying around isn't much of a maintenance burden, so I'd keep it there just in case.

----

`QueryOption` extends Slick with three new methods: `take1`, `update1` and `delete1`. The idea is that we usually use a WHERE clause which identifies a single row to be fetched, updated or edited, but Slick's API will consider the operation a success if zero, two, or more rows are affected.

For UPDATEs and DELETEs, we're not doing anything with the result, so this will be a silent failure. This version checks that exactly one row was affected and throws an exception otherwise. This will help us identify situations in which our code is incorrect or in which our database is inconsistent.

For SELECTs, it is possible to express the fact that we only expect a single row using LIMIT 1, but Slick's API still returns a list and it feels a bit unsafe to do a `get(0)` on that list to fetch the only result. And indeed, it is unsafe: LIMIT 1 guarantees that there is at most one result, but it doesn't guarantee that there is at least one. So the proper way to represent the result is not a list, but an `Option`. The `take1` method does a `take(1)` and wraps the result in an `Option` instead of a list, encouraging the caller to add some error-checking code to throw a more readable exception if zero rows were returned.

----

`StringBasedEnumeration` is used like this:

```scala
object Environment extends StringBasedEnumeration {
  val Dev  = Value("dev")
  val QA   = Value("qa")
  val Prod = Value("prod")
}

val env: Environment.Value = Environment.Dev
```

The result is very similar to an enumeration based on case classes:

```scala
sealed trait Env
case object Dev  extends Env
case object QA   extends Env
case object Prod extends Env
```

The difference is that `StringBasedEnumeration` defines methods to convert between a value of type `Environment.Value` and a string, and that those methods are automatically used by Slick and spray-json when serializing such a value to JSON or to the database.

Note that the type really is `Environment.Value`, not `Environment`. It would make more sense if `Environment` was the companion object of a type named `Environment`, but I don't know how to re-expose an existing type under a different top-level name.

----

`TransactionalFuture` is a version of `Future` which does _not_ run in parallel with other `TransactionalFuture` computations, on the contrary, a mutex is used to make sure that at most one such computation runs at a time. The idea is to provide critical sections using a very familiar interface: it's a regular Future, so like all Futures it can take some time to complete, in this case because it could be waiting to obtain the lock. This version is very simple, there is only one global lock so there are no race conditions, but I assume it could be extended to support multiple locks if we end up using this a lot.

The only place in which this is used is in `mintosci-zendesk`, to obtain transactional semantics despite the fact that Slick's transactional guarantees are very weak.

----

`TypedValue` is used a lot, it's used to distinguish types like `OrgId` and `UserId` which are semantically distinct despite having the same underlying representation. Like `StringBasedEnumeration`, the main advantage is to make it easier to define spray-json and Slick conversions by delegating the bulk of the work to the underlying representation.

----

`Unzip4` is... what is this? I think it's for transposing a list of four-tuples into a four-tuples of lists.
