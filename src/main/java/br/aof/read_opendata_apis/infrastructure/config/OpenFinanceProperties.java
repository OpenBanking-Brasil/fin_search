package br.aof.read_opendata_apis.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuração do Open Finance Brasil / Open Banking Brasil.
 * <p>
 * Dados Abertos (opendata): URLs vêm do Diretório Central — leitura HTTP sem OAuth para os
 * catálogos públicos de produtos/serviços, conforme publicado por cada participante.
 * Dados do cliente: exigem registro como participante/TPP, mTLS, consentimento e scopes —
 * ver manual técnico na Área do Desenvolvedor Open Finance Brasil.
 */
@ConfigurationProperties(prefix = "open-finance")
public class OpenFinanceProperties {

    private final Directory directory = new Directory();
    private final Opendata opendata = new Opendata();

    public Directory getDirectory() {
        return directory;
    }

    public Opendata getOpendata() {
        return opendata;
    }

    public static class Directory {
        /**
         * Endpoint oficial do Diretório de Participantes (ambiente de dados / produção).
         * Documentação: Área do Desenvolvedor — Open Finance Brasil.
         */
        private String participantsUrl = "https://data.directory.openbankingbrasil.org.br/participants";

        public String getParticipantsUrl() {
            return participantsUrl;
        }

        public void setParticipantsUrl(String participantsUrl) {
            this.participantsUrl = participantsUrl;
        }
    }

    public static class Opendata {
        /**
         * Se vazio, inclui todas as instituições com endpoints opendata.
         * Se preenchido (ex.: "Nubank", "Nu"), só mantém servidores cujo nome amigável
         * contém alguma das strings (ignora maiúsculas/minúsculas).
         */
        private List<String> institutionInclude = new ArrayList<>();

        public List<String> getInstitutionInclude() {
            return institutionInclude;
        }

        public void setInstitutionInclude(List<String> institutionInclude) {
            this.institutionInclude = institutionInclude;
        }
    }
}
