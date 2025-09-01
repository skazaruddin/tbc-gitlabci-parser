
"""
GitLab CI/CD Template Parser for Python using ruamel.yaml for round-trip formatting.
Provides similar functionality to the Java implementation.
"""

from ruamel.yaml import YAML
from typing import Dict, List, Any, Optional, Union
import logging

logger = logging.getLogger(__name__)

class GitLabParserOptions:
    """Configuration options for GitLab template parsing."""
    def __init__(self, indent: int = 2, width: int = 120, preserve_quotes: bool = True):
        self.indent = indent
        self.width = width
        self.preserve_quotes = preserve_quotes
    @classmethod
    def get_default(cls):
        return cls()

class GitLabTemplate:
    """Represents a parsed GitLab CI/CD template with utility methods."""
    def __init__(self, data: Dict[str, Any], original_content: Optional[str] = None):
        self.data = data
        self.original_content = original_content
    def get_data(self) -> Dict[str, Any]:
        return self.data
    def get_original_content(self) -> Optional[str]:
        return self.original_content
    def has_job(self, job_name: str) -> bool:
        return job_name in self.data and isinstance(self.data[job_name], dict)
    def get_job(self, job_name: str) -> Optional[Dict[str, Any]]:
        if self.has_job(job_name):
            return self.data[job_name]
        return None
    def get_include(self) -> List[Any]:
        include_data = self.data.get('include', [])
        return include_data if isinstance(include_data, list) else [include_data]
    def has_tbc_components(self) -> bool:
        includes = self.get_include()
        for item in includes:
            if isinstance(item, dict) and 'component' in item:
                component = item['component']
                if isinstance(component, str) and 'to-be-continuous' in component:
                    return True
        return False
    def get_components_by_path(self, path: str) -> List[Dict[str, Any]]:
        components = []
        includes = self.get_include()
        for item in includes:
            if isinstance(item, dict) and 'component' in item:
                component = item['component']
                if isinstance(component, str) and path in component:
                    components.append(item)
        return components
    
    def get_variables(self) -> Dict[str, Any]:
        """Get global variables section."""
        return self.data.get('variables', {})
    
    def get_all_image_references(self) -> List[str]:
        """Get all image references in the template."""
        images = []
        
        # Check global image
        if 'image' in self.data:
            image_val = self.data['image']
            if isinstance(image_val, str):
                images.append(image_val)
            elif isinstance(image_val, dict) and 'name' in image_val:
                images.append(image_val['name'])
        
        # Check job images
        for key, value in self.data.items():
            if isinstance(value, dict) and 'image' in value:
                image_val = value['image']
                if isinstance(image_val, str):
                    images.append(image_val)
                elif isinstance(image_val, dict) and 'name' in image_val:
                    images.append(image_val['name'])
        
        return images


    def parse_from_string(self, yaml_content: str) -> 'GitLabTemplate':
        """Parse GitLab CI template from string content using ruamel.yaml RoundTripLoader for formatting preservation."""
        if not yaml_content or not yaml_content.strip():
            raise ValueError("YAML content cannot be null or empty")
        from ruamel.yaml.constructor import ConstructorError
        from ruamel.yaml.nodes import ScalarNode, SequenceNode, MappingNode
        from ruamel.yaml import RoundTripLoader, add_constructor
        yaml = YAML()
        yaml.preserve_quotes = True
        yaml.indent(mapping=self.options.indent, sequence=self.options.indent, offset=0)
        yaml.width = self.options.width
        def ref_constructor(loader, node):
            if not isinstance(node, SequenceNode):
                raise ConstructorError(None, None, "!reference must be followed by a sequence", node.start_mark)
            values = loader.construct_sequence(node)
            if len(values) != 2:
                raise ConstructorError(None, None, "!reference must have exactly 2 elements: [anchor, property]", node.start_mark)
            return {
                '__gitlab_reference': True,
                'anchor': values[0],
                'property': values[1]
            }
        def include_constructor(loader, node):
            if isinstance(node, ScalarNode):
                value = loader.construct_scalar(node)
            elif isinstance(node, SequenceNode):
                value = loader.construct_sequence(node)
            else:
                value = loader.construct_mapping(node)
            return {
                '__gitlab_include': True,
                'value': value
            }
class GitLabTemplateParser:
    def __init__(self, options: Optional[GitLabParserOptions] = None):
        self.options = options or GitLabParserOptions.get_default()

    def parse_from_string(self, yaml_content: str) -> 'GitLabTemplate':
        """Parse GitLab CI template from string content using ruamel.yaml RoundTripLoader for formatting preservation."""
        if not yaml_content or not yaml_content.strip():
            raise ValueError("YAML content cannot be null or empty")
        from ruamel.yaml.constructor import ConstructorError
        from ruamel.yaml.nodes import ScalarNode, SequenceNode, MappingNode
        from ruamel.yaml import RoundTripLoader, add_constructor
        yaml = YAML()
        yaml.preserve_quotes = True
        yaml.allow_unicode = True
        yaml.allow_duplicate_keys = True
        # yaml.indent(mapping=self.options.indent, sequence=self.options.indent, offset=0)
        # yaml.width = self.options.width
        def ref_constructor(loader, node):
            if not isinstance(node, SequenceNode):
                raise ConstructorError(None, None, "!reference must be followed by a sequence", node.start_mark)
            values = loader.construct_sequence(node)
            if len(values) != 2:
                raise ConstructorError(None, None, "!reference must have exactly 2 elements: [anchor, property]", node.start_mark)
            return {
                '__gitlab_reference': True,
                'anchor': values[0],
                'property': values[1]
            }
        def include_constructor(loader, node):
            if isinstance(node, ScalarNode):
                value = loader.construct_scalar(node)
            elif isinstance(node, SequenceNode):
                value = loader.construct_sequence(node)
            else:
                value = loader.construct_mapping(node)
            return {
                '__gitlab_include': True,
                'value': value
            }
        add_constructor('!reference', ref_constructor, Loader=RoundTripLoader)
        add_constructor('!include', include_constructor, Loader=RoundTripLoader)
        data = yaml.load(yaml_content)
        return GitLabTemplate(data, yaml_content)
    def apply_changes_to_file(self, file_path: str, transform_fn=None):
        """
        Read a GitLab CI YAML file, apply changes, and write back to the file.
        By default, applies apply_java21_upgrade. You can pass a custom transform_fn.
        """
        template = self.parse_from_file(file_path)
        if transform_fn is None:
            transform_fn = self.apply_java21_upgrade
        updated_template = transform_fn(template)
        yaml = YAML()
        yaml.indent(mapping=self.options.indent, sequence=self.options.indent, offset=0)
        yaml.width = self.options.width
        with open(file_path, 'w', encoding='utf-8') as f:
            yaml.dump(updated_template.get_data(), f)
    """Parser for GitLab CI/CD templates using PyYAML with custom constructors."""
    
    def __init__(self, options: Optional[GitLabParserOptions] = None):
        self.options = options or GitLabParserOptions.get_default()
    
    def parse_from_file(self, file_path: str) -> 'GitLabTemplate':
        """Parse GitLab CI template from file."""
        try:
            with open(file_path, 'r', encoding='utf-8') as file:
                content = file.read()
                return self.parse_from_string(content)
        except IOError as e:
            logger.error(f"Failed to read file {file_path}: {e}")
            raise ValueError(f"Failed to read file: {e}") from e
    
    
    def update_image_in_section(self, template: 'GitLabTemplate', component_path: str, new_image: str) -> 'GitLabTemplate':
        """Update image tags in component inputs sections."""
        if template is None:
            raise ValueError("GitLabTemplate cannot be null")
        if not component_path or not component_path.strip():
            raise ValueError("Component path cannot be null or empty")
        if not new_image or not new_image.strip():
            raise ValueError("New image cannot be null or empty")
        
        try:
            data = template.get_data().copy()
            self._update_image_in_component_inputs(data, component_path, new_image)
            self._update_image_in_jobs(data, new_image)
            
            return GitLabTemplate(data, template.get_original_content())
            
        except Exception as e:
            logger.error(f"Failed to update image in template section: {e}")
            raise ValueError(f"Failed to update image: {e}") from e
    
    def _update_image_in_component_inputs(self, data: Dict[str, Any], component_path: str, new_image: str):
        """Update image in component inputs."""
        include_obj = data.get('include')
        if not isinstance(include_obj, list):
            return
        
        for item in include_obj:
            if not isinstance(item, dict):
                continue
            
            component = item.get('component')
            if isinstance(component, str) and component_path in component:
                inputs = item.get('inputs')
                if isinstance(inputs, dict):
                    inputs['image'] = new_image
                    logger.debug(f"Updated image in component {component_path} to {new_image}")
    
    def _update_image_in_jobs(self, data: Dict[str, Any], new_image: str):
        """Update image in job definitions."""
        for key, value in data.items():
            if isinstance(value, dict) and 'image' in value:
                image_obj = value['image']
                if isinstance(image_obj, str):
                    if self._should_update_image(image_obj, new_image):
                        value['image'] = new_image
                        logger.debug(f"Updated image in job {key} to {new_image}")
                elif isinstance(image_obj, dict) and 'name' in image_obj:
                    current_image = image_obj['name']
                    if isinstance(current_image, str) and self._should_update_image(current_image, new_image):
                        image_obj['name'] = new_image
                        logger.debug(f"Updated image name in job {key} to {new_image}")
    
    def _should_update_image(self, current_image: str, new_image: str) -> bool:
        """Check if image should be updated based on image name matching."""
        if not current_image or not new_image:
            return False
        
        current_name = self._extract_image_name(current_image)
        new_name = self._extract_image_name(new_image)
        
        # Update if the image names match regardless of tag
        return current_name == new_name and current_image != new_image
    
    def _extract_image_name(self, image: str) -> str:
        """Extract image name from full image reference."""
        if '/' in image:
            # Handle registry/namespace/name:tag format
            parts = image.split('/')
            name_with_tag = parts[-1]
            if ':' in name_with_tag:
                return name_with_tag.split(':')[0]
            return name_with_tag
        elif ':' in image:
            # Handle simple name:tag format
            return image.split(':')[0]
        return image
    
    def get_options(self) -> GitLabParserOptions:
        """Get parser options."""
        return self.options

    def apply_java21_upgrade(self, file_path: str):
        """
        Upgrade Maven component and jobs to use Java 21, and update Sonar scanner CLI image in a GitLab CI YAML file.
        - Maven component image set to 'maven:3.8-openjdk-21'
        - Maven job image set to 'maven:3.8-openjdk-21'
        - Sonar scanner job image set to 'sonarsource/sonar-scanner-cli:latest'
        The file is updated in-place.
        """
        template = self.parse_from_file(file_path)
        data = template.get_data().copy()

        # Update Maven component image
        self._update_image_in_component_inputs(data, "maven", "maven:3.8-openjdk-21")

        # Update Maven job image
        self._update_image_in_jobs(data, "maven:3.8-openjdk-21")

        # Update Sonar scanner CLI image in jobs
        for key, value in data.items():
            if isinstance(value, dict) and 'image' in value:
                # Heuristic: job name or image contains 'sonar' or 'scanner'
                job_name = key.lower()
                image_val = value['image']
                if ("sonar" in job_name or "scanner" in job_name or
                    (isinstance(image_val, str) and "sonar" in image_val)):
                    value['image'] = "sonarsource/sonar-scanner-cli:latest"
                elif isinstance(image_val, dict) and 'name' in image_val and "sonar" in image_val['name']:
                    image_val['name'] = "sonarsource/sonar-scanner-cli:latest"

        yaml = YAML()
        # yaml.indent(mapping=self.options.indent, sequence=self.options.indent, offset=0)
        # yaml.width = self.options.width
        with open(file_path, 'w', encoding='utf-8') as f:
            yaml.dump(data, f)