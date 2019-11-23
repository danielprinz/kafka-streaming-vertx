local-kafka: export SWARM_HOST = localhost
local-kafka: deploy-stack

deploy-stack: info
	docker stack deploy --with-registry-auth --prune --compose-file kafka.stack.yml kafka

# report environment variables
.PHONY: info
info: ## print all resolved variables for debugging purposes
	$(foreach V, $(sort $(.VARIABLES)), \
    $(if $(filter-out environment% default automatic,$(origin $V)), \
      $(info $V = $($V) [$(value $V)]) \
    ) \
  )
	@echo " === running on $(shell hostname)\n"
