package com.gilt.handlebars.visitor

import com.gilt.handlebars.context.{ClassCacheableContextFactory, Context}
import com.gilt.handlebars.logging.Loggable
import com.gilt.handlebars.parser._
import com.gilt.handlebars.parser.Content
import com.gilt.handlebars.parser.Comment
import com.gilt.handlebars.parser.Program
import com.gilt.handlebars.helper.{HelperOptionsBuilder, HelperOptions, Helper}
import com.gilt.handlebars.Handlebars

object DefaultVisitor extends ClassCacheableContextFactory {
  def apply[T](base: T, partials: Map[String, Handlebars], helpers: Map[String, Helper], data: Map[String, Any]) = {
    new DefaultVisitor[T](createRoot(base), partials, helpers, data)
  }
}

class DefaultVisitor[T](context: Context[T], partials: Map[String, Handlebars], helpers: Map[String, Helper], data: Map[String, Any]) extends Visitor with Loggable with ClassCacheableContextFactory {
  def visit(node: Node): String = node match {
    case Content(value) => value
    case Comment(_) => ""
    case Program(statements, inverse) => statements.map(visit).mkString
    case mustache:Mustache => {
      // I. There is no hash present on this {{mustache}}
      if(mustache.hash.value.isEmpty) {
        // 1. Check if path refers to a helper
        val value = helpers.get(mustache.path.string).map {
          callHelper(_, mustache, mustache.params)
        }.orElse {
        // 2. Check if path exists directly in the context
          context.lookup(mustache.path, mustache.params).asOption.map {
            _.model.toString
          }
        }.orElse {
        // 3. Check if path refers to provided data.
          data.get(mustache.path.string).map {
            // 3a. Check if path resolved to an IdentifierNode, probably the result of something that looks like
            //     {{path foo=bar.baz}}. 'bar.baz' in this case was converted to an IdentifierNode
            case i:IdentifierNode => context.lookup(i).asOption.map(_.model.toString).getOrElse("")

            // 3b. The data was something else, convert it to a string
            case other => other.toString
          }
        }.getOrElse {
        // 4. Could not find path in context, helpers or data.
          warn("Could not find path or helper: %s, context: %s".format(mustache.path, context))
          ""
        }

        escapeMustache(value, mustache.unescaped)
      } else {
      // II. There is a hash on this {{mustache}}. Start over with the hash information added to 'data'. All of the
      //     data in the hash will be accessible to any child nodes of this {{mustache}}.
        new DefaultVisitor(context, partials, helpers, data ++ hashNode2DataMap(mustache.hash)).visit(mustache.copy(hash = HashNode(Map.empty)))
      }

    }
    case block:Block => {
      // I. There is no hash present on this block
      if (block.mustache.hash.value.isEmpty) {
        val lookedUpCtx = context.lookup(block.mustache.path)
        // 1. Check if path refers to a helper
        helpers.get(block.mustache.path.string).map {
          callHelper(_, block.program, block.mustache.params)
        }.orElse {
        // 2. Check if path exists directly in the context
          lookedUpCtx.asOption.map {
            ctx =>
              renderBlock(ctx, block.program, block.inverse)
          }
        }.getOrElse {
        // 3. path was not found in helpers or context, it will be 'falsy' by default
          renderBlock(lookedUpCtx, block.program, block.inverse)
        }
      } else {
      // II. There is a hash on this block. Start over with the hash information added to 'data'. All of the
      //     data in the hash will be accessible to any child nodes of this block.
        val blockWithoutHash = block.copy(mustache = block.mustache.copy(hash = HashNode(Map.empty)))
        new DefaultVisitor(context, partials, helpers, data ++ hashNode2DataMap(block.mustache.hash)).visit(blockWithoutHash)
      }
    }
    case partial:Partial => {
      val partialName = (partial.name.value match {
        case i:IdentifierNode => i.string
        case o => o.value.toString
      }).replace("/", ".")

      val partialContext = partial.context.map(context.lookup(_)).getOrElse(context)
      partials.get(partialName).map {
        _(partialContext.model, data, partials, helpers)
      }.getOrElse {
        warn("Could not find partial: %s".format(partialName))
        ""
      }
    }
    case n => n.toString
  }

  protected def hashNode2DataMap(node: HashNode): Map[String, Any] = {
    node.value.map {
      case (key, value) => value match {
        case s:StringParameter => key -> s.value
        case i:IntegerParameter => key -> i.value
        case b:BooleanParameter => key -> b.value
        case other => key -> other
      }
    }
  }

  protected def escapeMustache(value: String, unescaped: Boolean = true): String = {
    if (unescaped) {
      value
    } else {
      scala.xml.Utility.escape(value)
    }
  }

  protected def renderBlock(ctx: Context[Any], program: Program, inverse: Option[Program]): String = {
    if (ctx.truthValue) {
      ctx.model match {
        case l:Iterable[_] => l.zipWithIndex.map {
          case (item, idx) => new DefaultVisitor(createChild(item, ctx), partials, helpers, data + ("index" -> idx)).visit(program)
        }.mkString
        case model =>
          new DefaultVisitor(createChild(model, context), partials, helpers, data).visit(program)
      }
    } else {
      inverse.map(visit).getOrElse("")
    }
  }

  protected def callHelper(helper: Helper, program: Node, params: List[ValueNode]): String = {
    val optionsBuilder = new HelperOptionsBuilder(context, partials, helpers, data, program, params)
    helper.apply(context.model, optionsBuilder.build)
  }
}