package au.com.dius.pact.consumer.dsl;

import au.com.dius.pact.core.model.generators.Generators;
import au.com.dius.pact.core.model.matchingrules.Category;
import java.nio.charset.Charset;

public interface DslContent {

  Category getMatchers();

  Generators getGenerators();

  /**
   * This closes off the object graph build from the DSL in case any child objects have not been closed.
   * @return The root object of the object graph
   */
  DslContent close();

  /**
   * Gets the DSL Content as bytes, using the default character set.
   *
   * @return A byte array, or null if no content is present
   */
  default byte[] toBytes() {
    return toBytes(Charset.defaultCharset());
  }

  /**
   * Gets the DSL Content as bytes.
   *
   * @param charset character set for encoding
   * @return A byte array, or null if no content is present
   */
  byte[] toBytes(Charset charset);
}
