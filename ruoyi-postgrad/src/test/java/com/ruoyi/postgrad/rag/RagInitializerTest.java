package com.ruoyi.postgrad.rag;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class RagInitializerTest {

    @Test
    void shouldSkipWhenAutoIndexDisabled() throws Exception {
        RagDocumentService service = mock(RagDocumentService.class);
        RagInitializer initializer = new RagInitializer(service);
        inject(initializer, "autoIndexOnStartup", false);
        inject(initializer, "apiKey", "key");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(service, never()).reindex();
    }

    @Test
    void shouldInvokeReindexWhenEnabled() throws Exception {
        RagDocumentService service = mock(RagDocumentService.class);
        RagInitializer initializer = new RagInitializer(service);
        inject(initializer, "autoIndexOnStartup", true);
        inject(initializer, "apiKey", "key");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(service).reindex();
    }

    @Test
    void shouldStillInvokeReindexWhenKeyBlankForGracefulDegrade() throws Exception {
        RagDocumentService service = mock(RagDocumentService.class);
        RagInitializer initializer = new RagInitializer(service);
        inject(initializer, "autoIndexOnStartup", true);
        inject(initializer, "apiKey", " ");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(service).reindex();
    }

    @Test
    void shouldNotFailStartupWhenReindexThrows() throws Exception {
        RagDocumentService service = mock(RagDocumentService.class);
        doThrow(new IllegalStateException("Invalid token")).when(service).reindex();
        RagInitializer initializer = new RagInitializer(service);
        inject(initializer, "autoIndexOnStartup", true);
        inject(initializer, "apiKey", "key");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(service).reindex();
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = RagInitializer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
