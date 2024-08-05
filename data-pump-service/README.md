# Comparabilidade de Dados Abertos

## Contexto

Arquitetura baseada em microsserviços para apoiar o uso das APIs Dados Abertos do Open Finance Brasil (OFB) na criação de ferramentas de comparabilidade de produtos financeiros entre instituições financeiras participantes do OFB.​

O que é disponibilizado neste serviço:​

* Pesquisa ao Diretório Central para extrair as URLs do OFB existentes em cada instituição financeira;​
* Filtra as URLs existentes relativas a Dados Abertos;​
* Extrai o payload de cada API para posterior analise e comparação de dados.

## O que posso fazer com esse microsserviço

Considerando que o microsserviço em questão é público e que qualquer desenvolvedor pode utilizar o código integralmente ou modificá-lo, pode-se usar a extração do mesmo para uma conexão como fonte de entrada em algum software de analytics ou até mesmo integrá-lo a outros microsserviços para desempenhar algum papel utilizando as APIs de Dados Abertos do OFB.

## Estrutura da aplicação

Estamos seguindo a estrategia de isolamento do dominio, para isso estamos seguindo conceitos importantes de desenvolvimento
como clean arquitecture, DDD e Hexagonal.

A estrutura dos pacotes ficou definida como:

```
    │   │                       ├───application
    │   │                       │   ├───dto
    │   │                       │   ├───exception
    │   │                       │   └───service
    │   │                       ├───domain
    │   │                       │   ├───event
    │   │                       │   └───exception
    │   │                       └───infrastructure
    │   │                           └───scheduler
```

Sendo:

Application: Porta de entrada da nossa aplicação, contem tudo que precisamos para conectar outros sistemas com o nosso domínio
como, por exemplo, o controller com os endpoints da aplicação, seguindo o contrato da api. A comunicação dessa camada com o domain é feita
através de interfaces definidas no **inbound** no pacote de domínio.

Domain: Essa camada vai conter toda a nossa regra de negócios, é aqui que fazemos a implementação dos nossos services (casos de uso) e das nossas
classes de domínio.

Infrastructure: Toda a comunicação e configuração externa do nosso sistema. A comunicação com esse modulo é feito através de interfaces definidas no 
**outbound** do nosso sistema, visando o desacoplamento da solução com os detalhes de infraestrutura.

## Tecnologias
- [Spring Boot] - Para otimizacao do trabalho e foco na regra de negocio
- [Swagger] - Para documentacao dos servicos criados
- [Intellij Community] - IDE Editor java
- [GIT] - Para gestao dos fontes do projeto
- [Java 11] - Versão do java para compilacao do sistema
- [Maven] - para gestao de dependencias

## Desenvolvimento local

Url após subir a app : http://localhost:8080/swagger-ui/index.html

### Rodando os testes
```
mvn clean install
```

### Rodando a aplicação no Docker Compose

```
docker compose up -d
```

### Rodando a aplicação
```
$ java -jar target/<app-name>-1.0-SNAPSHOT.jar

ou

$ mvn spring-boot:run 
```

## Rodando a aplicação em modo de produção
```
$ java -jar target/<app-name>-1.0-SNAPSHOT.jar --spring.profiles.default=prod

ou

$ mvn spring-boot:run -Dspring-boot.run.profiles=prod
```


