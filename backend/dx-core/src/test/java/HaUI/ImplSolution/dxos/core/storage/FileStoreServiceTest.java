// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.audit.AuditEvent;
import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.storage.FileProcessor;
import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.core.api.storage.ProcessedFile;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class FileStoreServiceTest {

  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final ClamAvScanner scanner = mock(ClamAvScanner.class);
  private final AuditRecorder audit = mock(AuditRecorder.class);
  private final FileProcessor processor = mock(FileProcessor.class);
  private final byte[] bytes = {1, 2, 3};

  @SuppressWarnings("unchecked")
  private FileStoreService service() {
    ObjectProvider<FileProcessor> providers = mock(ObjectProvider.class);
    when(providers.orderedStream()).thenReturn(Stream.of(processor));
    when(processor.purpose()).thenReturn("photo");
    when(processor.process(any(), any())).thenReturn(new ProcessedFile(bytes, "image/png", "png"));
    return new FileStoreService(storage, scanner, audit, providers);
  }

  @Test
  void infectedFileIsRefusedAuditedAndNeverStored() {
    when(scanner.enabled()).thenReturn(true);
    when(scanner.scan(bytes)).thenReturn(Optional.of("Eicar-Test-Signature"));
    FileStoreService service = service();

    assertThatThrownBy(() -> service.store("photo", bytes, "a.png", "u1"))
        .isInstanceOf(PokaYokeViolationException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ERR_FILE_INFECTED");

    ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
    verify(audit).record(event.capture());
    assertThat(event.getValue().action()).isEqualTo("FILE_INFECTED");
    assertThat(event.getValue().metadata()).containsEntry("signature", "Eicar-Test-Signature");
    verify(storage, never()).putObject(any(), any(), any(), any());
    verify(processor, never()).process(any(), any());
  }

  @Test
  void cleanFileIsStored() {
    when(scanner.enabled()).thenReturn(true);
    when(scanner.scan(bytes)).thenReturn(Optional.empty());

    assertThat(service().store("photo", bytes, "a.png", "u1")).endsWith(".png");

    verify(storage).putObject(any(), any(), any(), any());
  }

  @Test
  void scannerThatIsOffIsNotAsked() {
    when(scanner.enabled()).thenReturn(false);

    service().store("photo", bytes, "a.png", "u1");

    verify(scanner, never()).scan(any());
    verify(storage).putObject(any(), any(), any(), any());
  }

  @Test
  void fileIsNotStoredWhenTheScannerCannotAnswer() {
    when(scanner.enabled()).thenReturn(true);
    when(scanner.scan(bytes)).thenThrow(new ScannerUnavailableException("down", null));
    FileStoreService service = service();

    assertThatThrownBy(() -> service.store("photo", bytes, "a.png", "u1"))
        .isInstanceOf(ScannerUnavailableException.class);
    verify(storage, never()).putObject(any(), any(), any(), any());
  }
}
