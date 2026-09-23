package com.hr.recruitment.common;

import com.hr.recruitment.candidate.CandidateController;
import com.hr.recruitment.candidate.CandidateRepository;
import com.hr.recruitment.candidate.CandidateService;

import javax.sql.DataSource;

/**
 * Wires the DataSource, repositories, services, controllers, and the Router once per
 * Lambda container (cold start). Reused across warm invocations.
 */
public final class AppContext {

    private final Router router;

    private AppContext(Router router) {
        this.router = router;
    }

    public static AppContext create() {
        DataSource dataSource = DataSourceFactory.create();
        return createWithDataSource(dataSource);
    }

    /** Used by local/LocalServer and integration tests, which supply their own DataSource. */
    public static AppContext createWithDataSource(DataSource dataSource) {
        CandidateRepository candidateRepository = new CandidateRepository(dataSource);
        CandidateService candidateService = new CandidateService(candidateRepository);
        CandidateController candidateController = new CandidateController(candidateService);

        Router router = new Router()
            .register("POST", "/api/v1/candidates", (event, params, requestId) -> candidateController.create(event, params))
            .register("GET", "/api/v1/candidates/{id}", (event, params, requestId) -> candidateController.getById(event, params))
            .register("PUT", "/api/v1/candidates/{id}", (event, params, requestId) -> candidateController.update(event, params));

        return new AppContext(router);
    }

    public Router router() {
        return router;
    }
}
