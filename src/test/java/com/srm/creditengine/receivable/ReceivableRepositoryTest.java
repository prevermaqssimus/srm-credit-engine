package com.srm.creditengine.receivable;

import com.srm.creditengine.domain.enums.Currency;
import com.srm.creditengine.domain.enums.ReceivableType;
import com.srm.creditengine.domain.model.Receivable;
import com.srm.creditengine.receivable.repository.ReceivableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Passo 10 do plano: "Resultado esperado: CRUD básico validado contra H2".
 *
 * @DataJpaTest sobe um contexto Spring MINIMO (só a camada JPA/repositório),
 * usando H2 in-memory automaticamente -- não precisa configurar datasource
 * manualmente, o Spring Boot detecta o H2 (dependência de teste já presente
 * no pom.xml) e usa por padrão neste tipo de teste.
 *
 * Cada teste roda dentro de uma transação que é revertida (rollback) ao
 * final -- por isso não há necessidade de limpar o banco manualmente entre
 * os testes.
 */
@DataJpaTest
class ReceivableRepositoryTest {

    @Autowired
    private ReceivableRepository receivableRepository;

    @Test
    void save_persistsReceivable_andGeneratesId() {
        Receivable receivable = new Receivable(
                ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("100000"), 3,
                Currency.BRL, "Cedente Teste", "Sacado Teste");

        Receivable saved = receivableRepository.save(receivable);

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void findById_returnsReceivable_whenExists() {
        Receivable saved = receivableRepository.save(new Receivable(
                ReceivableType.CHEQUE_PRE_DATADO, new BigDecimal("25000"), 2,
                Currency.BRL, "Cedente X", "Sacado Y"));

        Optional<Receivable> found = receivableRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCedente()).isEqualTo("Cedente X");
    }

    @Test
    void findById_returnsEmpty_whenIdDoesNotExist() {
        Optional<Receivable> found = receivableRepository.findById(999999L);

        assertThat(found).isEmpty();
    }

    @Test
    void findAll_returnsAllSavedReceivables() {
        receivableRepository.save(new Receivable(
                ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("1000"), 1,
                Currency.BRL, "A", "B"));
        receivableRepository.save(new Receivable(
                ReceivableType.CHEQUE_PRE_DATADO, new BigDecimal("2000"), 2,
                Currency.USD, "C", "D"));

        assertThat(receivableRepository.findAll()).hasSize(2);
    }

    @Test
    void deleteById_removesReceivable() {
        Receivable saved = receivableRepository.save(new Receivable(
                ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("500"), 1,
                Currency.BRL, "Cedente Delete", "Sacado Delete"));

        receivableRepository.deleteById(saved.getId());

        assertThat(receivableRepository.findById(saved.getId())).isEmpty();
    }
}
