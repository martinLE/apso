package com.velocidi.apso.elasticsearch

import scala.concurrent.duration._
import scala.util.Random

import com.sksamuel.elastic4s.requests.indexes.CreateIndexTemplateRequest
import com.sksamuel.elastic4s.requests.mappings.{ KeywordField, MappingDefinition }
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicTemplateRequest
import com.sksamuel.elastic4s.testkit._
import org.scalatest.Suite
import org.specs2.mutable._
import org.specs2.specification._
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ ElasticClient, ElasticProperties }
import com.sksamuel.elastic4s.http.JavaClient
import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner
import org.elasticsearch.common.settings.Settings

trait ElasticsearchTestKit extends NoSpecElasticsearchTestKit with AfterAll {
  this: SpecificationLike =>

  def afterAll() = {
    runner.close()
    runner.clean()
  }
}

trait NoSpecElasticsearchTestKit {
  lazy val runner = new ElasticsearchClusterRunner().onBuild((_, settingsBuilder: Settings.Builder) => {
    settingsBuilder.put("http.cors.enabled", true)
    settingsBuilder.put("http.cors.allow-origin", "*")
    settingsBuilder.put("discovery.type", "single-node")
  })

  runner.build(
    ElasticsearchClusterRunner
      .newConfigs()
      .clusterName(s"es-testkit-${System.currentTimeMillis}")
      // Between the time es-cluster-runner check if a port is available and the time it actually binds it,
      // another es-cluster-runner instance could already be using that same port
      // By adding this Random we avoid binding to ports already in use
      .baseHttpPort(9200 + Random.nextInt(50))
      .baseTransportPort(9300 + Random.nextInt(50))
      .numOfNode(1)
      .disableESLogger()
      .useLogger())

  val httpPort = runner.getNode(0).settings().getAsInt("http.port", 9201)
  val underlying = new Suite with ClientProvider with ElasticSugar {
    val client = ElasticClient(JavaClient(ElasticProperties(s"http://localhost:$httpPort")))
  }

  lazy val esClient = underlying.client

  // base templates

  esClient.execute(
    CreateIndexTemplateRequest("default", "*")
      .settings(Map("number_of_replicas" -> 1, "number_of_shards" -> 1))
      .mappings(Iterable(MappingDefinition().dynamicTemplates(
        DynamicTemplateRequest("strings_as_keywords", KeywordField(""))
          .matchMappingType("string"))))).await(10.seconds)

  runner.ensureYellow()

  // utility methods

  def blockUntilDocCount(index: String, docCount: Int) = underlying.blockUntilCount(docCount, index)
  def doesIndexExists(index: String) = underlying.doesIndexExists(index)
  def ensureIndexExists(index: String) = underlying.ensureIndexExists(index)

  // FIXME: With a proper project structuring, we should be able to provide the test configuration here:
  //        `lazy val testEsConfig = Elasticsearch("localhost", httpPort, useHttps = false)`
  //        Right now, it is cumbersome to make this project depend on `core` to provide the configuration models here.
  lazy val host = "localhost"
  lazy val useHttps = false
}

