"""
Tests for GitLab CI/CD Template Parser Python implementation.
"""

import pytest
from gitlab_template_parser import GitLabTemplateParser, GitLabTemplate, GitLabParserOptions


class TestGitLabTemplateParser:
    
    def setup_method(self):
        self.parser = GitLabTemplateParser()
    
    def test_parse_simple_template(self):
        yaml_content = """
stages:
  - test
  - build

test-job:
  stage: test
  script:
    - echo "Testing"
"""
        template = self.parser.parse_from_string(yaml_content)
        
        assert template is not None
        assert template.has_job("test-job")
        assert "stages" in template.get_data()
    
    def test_gitlab_reference_tags(self):
        yaml_content = """
.maven_image: &maven_image
  image: maven:3.8-openjdk-11

.default_cache: &default_cache
  cache:
    key: ${CI_COMMIT_REF_SLUG}
    paths:
      - .m2/repository/

test-job:
  stage: test
  image: !reference [.maven_image, image]
  script:
    - mvn test
  <<: *default_cache
"""
        template = self.parser.parse_from_string(yaml_content)
        
        assert template is not None
        assert template.has_job("test-job")
        
        test_job = template.get_job("test-job")
        assert "image" in test_job
        
        # Check that !reference is parsed into structured representation
        image_value = test_job["image"]
        assert isinstance(image_value, dict)
        assert image_value.get("__gitlab_reference") is True
        assert image_value.get("anchor") == ".maven_image"
        assert image_value.get("property") == "image"
    
    def test_gitlab_include_tags(self):
        yaml_content = """
include:
  - !include local-template.yml
  - !include 
    - remote-template1.yml
    - remote-template2.yml

test-job:
  stage: test
  script:
    - echo "Testing"
"""
        template = self.parser.parse_from_string(yaml_content)
        
        assert template is not None
        includes = template.get_include()
        assert len(includes) == 2
        
        # Check first include (scalar)
        first_include = includes[0]
        assert isinstance(first_include, dict)
        assert first_include.get("__gitlab_include") is True
        assert first_include.get("value") == "local-template.yml"
        
        # Check second include (sequence)
        second_include = includes[1]
        assert isinstance(second_include, dict)
        assert second_include.get("__gitlab_include") is True
        assert isinstance(second_include.get("value"), list)
        assert len(second_include.get("value")) == 2
    
    def test_yaml_anchors_and_merge_keys(self):
        yaml_content = """
.cache_template: &cache_config
  cache:
    key: ${CI_COMMIT_REF_SLUG}
    paths:
      - .m2/repository/
    policy: pull-push

.maven_image: &maven_image
  image: maven:3.8-openjdk-11

validate-pom:
  stage: validate
  script:
    - echo "Running validation"
    - mvn validate
  <<: [*cache_config, *maven_image]
"""
        template = self.parser.parse_from_string(yaml_content)
        
        assert template is not None
        assert template.has_job("validate-pom")
        
        validate_job = template.get_job("validate-pom")
        assert "script" in validate_job
        assert "stage" in validate_job
        
        # Check merge key handling
        assert "<<" in validate_job or "cache" in validate_job or "image" in validate_job
    
    def test_tbc_components_detection(self):
        yaml_content = """
include:
  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
    inputs:
      image: "docker.io/library/maven:3.8-openjdk-11"
      deploy-enabled: true
  
  - component: $CI_SERVER_FQDN/to-be-continuous/gitleaks/gitlab-ci-gitleaks@2.0
    inputs:
      stage: security-scan

stages:
  - validate
  - security-scan
  - build
"""
        template = self.parser.parse_from_string(yaml_content)
        
        assert template is not None
        assert template.has_tbc_components()
        
        maven_components = template.get_components_by_path("maven")
        assert len(maven_components) == 1
        
        gitleaks_components = template.get_components_by_path("gitleaks")
        assert len(gitleaks_components) == 1
    
    def test_update_image_in_section(self):
        yaml_content = """
include:
  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
    inputs:
      image: "docker.io/library/maven:3.8-openjdk-11"

test-job:
  stage: test
  image: maven:3.8-openjdk-11
  script:
    - mvn test
"""
        template = self.parser.parse_from_string(yaml_content)
        updated_template = self.parser.update_image_in_section(template, "maven", "maven:3.8-openjdk-17")
        
        # Check component input image update
        includes = updated_template.get_include()
        maven_component = includes[0]
        assert maven_component["inputs"]["image"] == "maven:3.8-openjdk-17"
        
        # Check job image update
        test_job = updated_template.get_job("test-job")
        assert test_job["image"] == "maven:3.8-openjdk-17"
    
    def test_complex_job_configurations(self):
        yaml_content = """
services:
  - name: postgres:13
    alias: db
  - name: redis:6-alpine
    alias: cache

integration-test:
  image: 
    name: node:18-alpine
    entrypoint: [""]
  services:
    - name: postgres:13
      variables:
        POSTGRES_DB: testdb
        POSTGRES_USER: testuser
        POSTGRES_PASSWORD: testpass
  variables:
    DATABASE_URL: "postgresql://testuser:testpass@db:5432/testdb"
    REDIS_URL: "redis://cache:6379"
  before_script:
    - apk add --no-cache postgresql-client
    - until pg_isready -h db -p 5432 -U testuser; do sleep 1; done
  script:
    - npm run test:integration
  artifacts:
    reports:
      junit: test-results.xml
      coverage_report:
        coverage_format: cobertura
        path: coverage.xml
  coverage: '/Coverage: \\d+\\.\\d+%/'
  retry:
    max: 2
    when:
      - runner_system_failure
      - stuck_or_timeout_failure
"""
        template = self.parser.parse_from_string(yaml_content)
        
        assert template is not None
        assert "services" in template.get_data()
        assert template.has_job("integration-test")
        
        integration_job = template.get_job("integration-test")
        assert "services" in integration_job
        assert "artifacts" in integration_job
        assert "retry" in integration_job
        assert "coverage" in integration_job
        
        # Verify complex image structure
        image_config = integration_job["image"]
        assert isinstance(image_config, dict)
        assert "name" in image_config
        assert "entrypoint" in image_config
    
    def test_dump_to_string(self):
        yaml_content = """
stages:
  - test

test-job:
  stage: test
  script:
    - echo "Testing"
"""
        template = self.parser.parse_from_string(yaml_content)
        dumped = self.parser.dump_to_string(template)
        
        assert "stages:" in dumped
        assert "test-job:" in dumped
        assert "script:" in dumped
    
    def test_error_handling(self):
        # Test empty content
        with pytest.raises(ValueError, match="YAML content cannot be null or empty"):
            self.parser.parse_from_string("")
        
        # Test invalid YAML
        with pytest.raises(ValueError, match="Failed to parse YAML content"):
            self.parser.parse_from_string("invalid: yaml: content: [")
        
        # Test non-dict root
        with pytest.raises(ValueError, match="Root YAML element must be a map/object"):
            self.parser.parse_from_string("- item1\n- item2")


if __name__ == "__main__":
    pytest.main([__file__, "-v"])